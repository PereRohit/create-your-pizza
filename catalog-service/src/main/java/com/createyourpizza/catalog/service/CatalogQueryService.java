package com.createyourpizza.catalog.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.Pagination;
import com.createyourpizza.catalog.web.dto.ProductResponse;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

	static final int DEFAULT_PAGE_SIZE = 10;
	static final int MAX_PAGE_SIZE = 100;

	private final ProductRepository productRepository;
	private final OptionEntityRepository optionEntityRepository;
	private final CatalogResponseMapper mapper;

	@Transactional(readOnly = true)
	public CatalogListPage list(String categoryRaw, String typeRaw, BigDecimal maxPrice, Integer page, Integer size) {
		int currentPage = page == null || page < 1 ? 1 : page;
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

		ListType listType = parseType(typeRaw);
		ProductCategory category = listType == ListType.PIZZA_SPEC ? null : parseCategory(categoryRaw);

		boolean includeProducts = listType == null || listType.productType != null;
		boolean includeOptions = listType == ListType.PIZZA_SPEC || (listType == null && category == null);

		List<CatalogRow> rows = new ArrayList<>();
		if (includeProducts) {
			ProductType productType = listType == null ? null : listType.productType;
			for (Product product : productRepository.findAll(productSpec(productType, category, maxPrice))) {
				rows.add(new CatalogRow(product.getCreatedAt(), product.getId(), mapper.toProductResponse(product)));
			}
		}
		if (includeOptions) {
			for (OptionEntity option : optionEntityRepository.findAll(optionSpec(maxPrice))) {
				rows.add(new CatalogRow(option.getCreatedAt(), option.getId(), mapper.toOptionResponse(option)));
			}
		}

		rows.sort(Comparator.comparing(CatalogRow::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(CatalogRow::id));

		long total = rows.size();
		int from = Math.min((currentPage - 1) * pageSize, rows.size());
		int to = Math.min(from + pageSize, rows.size());
		List<Object> items = rows.subList(from, to).stream().map(CatalogRow::item).toList();
		int next = to < total ? currentPage + 1 : -1;
		return new CatalogListPage(items, new Pagination(currentPage, next, total));
	}

	@Transactional(readOnly = true)
	public ProductResponse getProduct(UUID id) {
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException("Product not found"));
		return mapper.toProductResponse(product);
	}

	@Transactional(readOnly = true)
	public OptionResponse getOption(UUID id) {
		OptionEntity option = optionEntityRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException("Option not found"));
		return mapper.toOptionResponse(option);
	}

	private static Specification<Product> productSpec(
			ProductType productType,
			ProductCategory category,
			BigDecimal maxPrice) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.isTrue(root.get("active")));
			if (productType != null) {
				predicates.add(cb.equal(root.get("productType"), productType));
			}
			if (category != null) {
				predicates.add(cb.equal(root.get("category"), category));
			}
			if (maxPrice != null) {
				predicates.add(cb.lessThan(root.get("price"), maxPrice));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static Specification<OptionEntity> optionSpec(BigDecimal maxPrice) {
		return (root, query, cb) -> {
			if (maxPrice == null) {
				return cb.conjunction();
			}
			return cb.lessThan(root.get("price"), maxPrice);
		};
	}

	private static ListType parseType(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return switch (raw) {
			case "simple" -> ListType.SIMPLE;
			case "combo" -> ListType.COMBO;
			case "pizza-base" -> ListType.PIZZA_BASE;
			case "pizza-spec" -> ListType.PIZZA_SPEC;
			default -> throw new CatalogBadRequestException("Unknown type: " + raw);
		};
	}

	private static ProductCategory parseCategory(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return ProductCategory.fromDbValue(raw);
		}
		catch (IllegalArgumentException ex) {
			throw new CatalogBadRequestException("Unknown category: " + raw);
		}
	}

	private enum ListType {
		SIMPLE(ProductType.simple),
		COMBO(ProductType.combo),
		PIZZA_BASE(ProductType.pizza),
		PIZZA_SPEC(null);

		private final ProductType productType;

		ListType(ProductType productType) {
			this.productType = productType;
		}
	}

	private record CatalogRow(Instant createdAt, UUID id, Object item) {
	}

	public record CatalogListPage(List<Object> items, Pagination pagination) {
	}
}
