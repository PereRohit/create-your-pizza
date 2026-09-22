package com.createyourpizza.catalog.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.createyourpizza.catalog.config.LockProperties;
import com.createyourpizza.catalog.domain.CatalogMeta;
import com.createyourpizza.catalog.domain.ComboItem;
import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.OptionKind;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.lock.CatalogLockKeys;
import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.repository.CatalogMetaRepository;
import com.createyourpizza.catalog.repository.ComboItemRepository;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.seed.CatalogMetaDefaults;
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.OptionWriteRequest;
import com.createyourpizza.catalog.web.dto.ProductResponse;
import com.createyourpizza.catalog.web.dto.ProductWriteRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogWriteService {

	private final CatalogLockStore lockStore;
	private final LockProperties lockProperties;
	private final TransactionTemplate transactionTemplate;
	private final ProductRepository productRepository;
	private final ComboItemRepository comboItemRepository;
	private final OptionEntityRepository optionEntityRepository;
	private final CatalogMetaRepository catalogMetaRepository;
	private final CatalogResponseMapper mapper;

	public ProductResponse createProduct(ProductWriteRequest request) {
		return withWriteLock(() -> {
			Product product = new Product();
			applyProductFields(product, request, true);
			Product saved = productRepository.save(product);
			replaceComboMembership(saved, request.getSimpleIds());
			markDirty();
			return mapper.toProductResponse(saved);
		});
	}

	public ProductResponse updateProduct(UUID id, ProductWriteRequest request) {
		return withWriteLock(() -> {
			Product product = productRepository.findById(id)
					.orElseThrow(() -> new CatalogNotFoundException("Product not found"));
			applyProductFields(product, request, false);
			Product saved = productRepository.save(product);
			replaceComboMembership(saved, request.getSimpleIds());
			markDirty();
			return mapper.toProductResponse(saved);
		});
	}

	public void deleteProduct(UUID id) {
		withWriteLock(() -> {
			Product product = productRepository.findById(id)
					.orElseThrow(() -> new CatalogNotFoundException("Product not found"));
			comboItemRepository.deleteByComboId(id);
			comboItemRepository.deleteBySimpleId(id);
			productRepository.delete(product);
			markDirty();
			return null;
		});
	}

	public OptionResponse createOption(OptionWriteRequest request) {
		return withWriteLock(() -> {
			OptionEntity option = new OptionEntity();
			applyOptionFields(option, request);
			OptionEntity saved = optionEntityRepository.save(option);
			markDirty();
			return mapper.toOptionResponse(saved);
		});
	}

	public OptionResponse updateOption(UUID id, OptionWriteRequest request) {
		return withWriteLock(() -> {
			OptionEntity option = optionEntityRepository.findById(id)
					.orElseThrow(() -> new CatalogNotFoundException("Option not found"));
			applyOptionFields(option, request);
			OptionEntity saved = optionEntityRepository.save(option);
			markDirty();
			return mapper.toOptionResponse(saved);
		});
	}

	public void deleteOption(UUID id) {
		withWriteLock(() -> {
			OptionEntity option = optionEntityRepository.findById(id)
					.orElseThrow(() -> new CatalogNotFoundException("Option not found"));
			optionEntityRepository.delete(option);
			markDirty();
			return null;
		});
	}

	private <T> T withWriteLock(Supplier<T> action) {
		if (lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)) {
			throw new CatalogBusyException();
		}
		String holder = UUID.randomUUID().toString();
		if (!lockStore.tryAcquire(CatalogLockKeys.CATALOG_WRITE, holder, lockProperties.getWriteTtl())) {
			throw new CatalogBusyException();
		}
		try {
			// TX must commit before lock DEL so the write lock covers the durable mutation (Design §6).
			return transactionTemplate.execute(status -> action.get());
		}
		finally {
			lockStore.release(CatalogLockKeys.CATALOG_WRITE);
		}
	}

	private void applyProductFields(Product product, ProductWriteRequest request, boolean creating) {
		ProductType type = parseProductType(request.getProductType());
		ProductCategory category = parseCategory(request.getProductCategory());

		if (creating) {
			product.setProductType(type);
		}
		else if (product.getProductType() != type) {
			throw new CatalogBadRequestException("productType cannot change");
		}

		product.setName(request.getProductName());
		product.setCategory(category);
		product.setPrice(request.getProductPrice());
		product.setActive(request.getActive() == null || request.getActive());

		if (type == ProductType.pizza) {
			if (request.getOptionsEnabled() == null) {
				throw new CatalogBadRequestException("optionsEnabled is required for pizza");
			}
			product.setOptionsEnabled(request.getOptionsEnabled());
			String notes = request.getCustomisationNotes();
			product.setCustomisationNotes(notes == null || notes.isBlank() ? null : notes);
		}
		else {
			if (request.getOptionsEnabled() != null && request.getOptionsEnabled()) {
				throw new CatalogBadRequestException("optionsEnabled is only valid for pizza");
			}
			if (request.getCustomisationNotes() != null && !request.getCustomisationNotes().isBlank()) {
				throw new CatalogBadRequestException("customisationNotes is only valid for pizza");
			}
			product.setOptionsEnabled(false);
			product.setCustomisationNotes(null);
		}

		if (type == ProductType.combo) {
			if (request.getSimpleIds() == null || request.getSimpleIds().isEmpty()) {
				throw new CatalogBadRequestException("simpleIds required for combo");
			}
		}
		else if (request.getSimpleIds() != null && !request.getSimpleIds().isEmpty()) {
			throw new CatalogBadRequestException("simpleIds is only valid for combo");
		}
	}

	private void replaceComboMembership(Product product, List<UUID> simpleIds) {
		if (product.getProductType() != ProductType.combo) {
			comboItemRepository.deleteByComboId(product.getId());
			return;
		}
		List<UUID> members = simpleIds == null ? List.of() : simpleIds;
		List<ComboItem> items = new ArrayList<>();
		for (UUID simpleId : members) {
			Product simple = productRepository.findById(simpleId)
					.orElseThrow(() -> new CatalogBadRequestException("Unknown simple product: " + simpleId));
			if (simple.getProductType() != ProductType.simple) {
				throw new CatalogBadRequestException("Combo member must be simple: " + simpleId);
			}
			items.add(new ComboItem(product.getId(), simpleId));
		}
		comboItemRepository.deleteByComboId(product.getId());
		comboItemRepository.saveAll(items);
	}

	private void applyOptionFields(OptionEntity option, OptionWriteRequest request) {
		option.setKind(parseOptionKind(request.getOptionKind()));
		option.setName(request.getProductName());
		option.setPrice(request.getProductPrice());
		option.setBase(Boolean.TRUE.equals(request.getIsBase()));
	}

	private void markDirty() {
		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID)
				.orElseGet(() -> CatalogMetaDefaults.newSeedRow(Instant.now()));
		meta.setDirty(true);
		meta.setLastCatalogChangeAt(Instant.now());
		// Do not change lastPdfVersion on writes (Design §3.3 / story AC).
		catalogMetaRepository.save(meta);
	}

	private static ProductType parseProductType(String raw) {
		if (raw == null) {
			throw new CatalogBadRequestException("productType is required");
		}
		return switch (raw) {
			case "simple" -> ProductType.simple;
			case "combo" -> ProductType.combo;
			case "pizza", "pizza-base" -> ProductType.pizza;
			default -> throw new CatalogBadRequestException("Unknown productType: " + raw);
		};
	}

	private static ProductCategory parseCategory(String raw) {
		try {
			return ProductCategory.fromDbValue(raw);
		}
		catch (IllegalArgumentException ex) {
			throw new CatalogBadRequestException("Unknown productCategory: " + raw);
		}
	}

	private static OptionKind parseOptionKind(String raw) {
		try {
			return OptionKind.valueOf(raw);
		}
		catch (RuntimeException ex) {
			throw new CatalogBadRequestException("Unknown optionKind: " + raw);
		}
	}
}
