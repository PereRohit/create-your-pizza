package com.createyourpizza.catalog.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.createyourpizza.catalog.cache.CatalogCacheKeys;
import com.createyourpizza.catalog.cache.CatalogCacheStore;
import com.createyourpizza.catalog.config.CacheProperties;
import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.Pagination;
import com.createyourpizza.catalog.web.dto.ProductResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
	private final CatalogCacheStore cacheStore;
	private final CacheProperties cacheProperties;
	private final ObjectMapper objectMapper = cacheObjectMapper();

	@Transactional(readOnly = true)
	public CatalogListPage list(String categoryRaw, String typeRaw, BigDecimal maxPrice, Integer page, Integer size) {
		int currentPage = page == null || page < 1 ? 1 : page;
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

		ListType listType = parseType(typeRaw);
		ProductCategory category = listType == ListType.PIZZA_SPEC ? null : parseCategory(categoryRaw);

		String cacheKey = CatalogCacheKeys.list(
				category == null ? null : category.getDbValue(),
				listType == null ? null : listType.wireType,
				maxPrice,
				currentPage,
				pageSize);
		Optional<CatalogListPage> cached = readCached(cacheKey, this::readList);
		if (cached.isPresent()) {
			return cached.get();
		}

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
		CatalogListPage result = new CatalogListPage(items, new Pagination(currentPage, next, total));
		writeCached(cacheKey, result);
		return result;
	}

	@Transactional(readOnly = true)
	public ProductResponse getProduct(UUID id) {
		String cacheKey = CatalogCacheKeys.product(id);
		Optional<ProductResponse> cached = readCached(cacheKey, json -> objectMapper.readValue(json, ProductResponse.class));
		if (cached.isPresent()) {
			return cached.get();
		}
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException("Product not found"));
		ProductResponse response = mapper.toProductResponse(product);
		writeCached(cacheKey, response);
		return response;
	}

	@Transactional(readOnly = true)
	public OptionResponse getOption(UUID id) {
		String cacheKey = CatalogCacheKeys.option(id);
		Optional<OptionResponse> cached = readCached(cacheKey, json -> objectMapper.readValue(json, OptionResponse.class));
		if (cached.isPresent()) {
			return cached.get();
		}
		OptionEntity option = optionEntityRepository.findById(id)
				.orElseThrow(() -> new CatalogNotFoundException("Option not found"));
		OptionResponse response = mapper.toOptionResponse(option);
		writeCached(cacheKey, response);
		return response;
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

	private <T> Optional<T> readCached(String key, JsonReader<T> reader) {
		return cacheStore.get(key).map(json -> {
			try {
				return reader.read(json);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to read catalog cache key " + key, ex);
			}
		});
	}

	private void writeCached(String key, Object value) {
		try {
			cacheStore.put(key, objectMapper.writeValueAsString(value), cacheProperties.getCatalogTtl());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write catalog cache key " + key, ex);
		}
	}

	private static ObjectMapper cacheObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		return mapper;
	}

	private CatalogListPage readList(String json) throws IOException {
		JsonNode root = objectMapper.readTree(json);
		Pagination pagination = objectMapper.treeToValue(root.get("pagination"), Pagination.class);
		List<Object> items = new ArrayList<>();
		for (JsonNode item : root.withArray("items")) {
			String productType = item.path("productType").asText();
			if ("pizza-spec".equals(productType)) {
				items.add(objectMapper.treeToValue(item, OptionResponse.class));
			}
			else {
				items.add(objectMapper.treeToValue(item, ProductResponse.class));
			}
		}
		return new CatalogListPage(items, pagination);
	}

	@FunctionalInterface
	private interface JsonReader<T> {
		T read(String json) throws IOException;
	}

	private enum ListType {
		SIMPLE(ProductType.simple, "simple"),
		COMBO(ProductType.combo, "combo"),
		PIZZA_BASE(ProductType.pizza, "pizza-base"),
		PIZZA_SPEC(null, "pizza-spec");

		private final ProductType productType;
		private final String wireType;

		ListType(ProductType productType, String wireType) {
			this.productType = productType;
			this.wireType = wireType;
		}
	}

	private record CatalogRow(Instant createdAt, UUID id, Object item) {
	}

	public record CatalogListPage(List<Object> items, Pagination pagination) {
	}
}
