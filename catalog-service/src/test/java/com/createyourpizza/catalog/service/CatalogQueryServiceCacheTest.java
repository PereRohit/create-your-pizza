package com.createyourpizza.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import com.createyourpizza.catalog.cache.CatalogCacheKeys;
import com.createyourpizza.catalog.cache.InMemoryCatalogCacheStore;
import com.createyourpizza.catalog.config.CacheProperties;
import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.OptionKind;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.repository.ComboItemRepository;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.ProductResponse;

@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceCacheTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private OptionEntityRepository optionEntityRepository;

	@Mock
	private ComboItemRepository comboItemRepository;

	private AtomicReference<Instant> now;
	private InMemoryCatalogCacheStore cacheStore;
	private CacheProperties cacheProperties;
	private CatalogQueryService service;

	@BeforeEach
	void setUp() {
		now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
		Clock clock = new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now.get();
			}
		};
		cacheStore = new InMemoryCatalogCacheStore(clock);
		cacheProperties = new CacheProperties();
		cacheProperties.setCatalogTtl(Duration.ofMinutes(3));
		service = new CatalogQueryService(
				productRepository,
				optionEntityRepository,
				new CatalogResponseMapper(comboItemRepository),
				cacheStore,
				cacheProperties);
	}

	@Test
	void listMissLoadsDbThenFillsCacheWithConfiguredTtl() {
		Product product = product("Garlic", Instant.parse("2026-01-01T00:00:00Z"), ProductType.simple);
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(product));
		when(optionEntityRepository.findAll(any(Specification.class))).thenReturn(List.of());

		CatalogQueryService.CatalogListPage page = service.list(null, null, null, 1, 10);

		assertThat(page.items()).hasSize(1);
		assertThat(((ProductResponse) page.items().getFirst()).getProductName()).isEqualTo("Garlic");
		String key = CatalogCacheKeys.list(null, null, null, 1, 10);
		assertThat(key).startsWith(CatalogCacheKeys.PREFIX);
		assertThat(cacheStore.get(key)).isPresent();

		now.set(now.get().plus(Duration.ofMinutes(3)).minusSeconds(1));
		assertThat(cacheStore.get(key)).isPresent();
	}

	@Test
	void listHitSkipsRepositories() {
		Product product = product("Garlic", Instant.parse("2026-01-01T00:00:00Z"), ProductType.simple);
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(product));

		service.list("veg", "simple", new BigDecimal("100.00"), 1, 10);
		CatalogQueryService.CatalogListPage hit = service.list("veg", "simple", new BigDecimal("100.0"), 1, 10);

		assertThat(((ProductResponse) hit.items().getFirst()).getProductName()).isEqualTo("Garlic");
		verify(productRepository, times(1)).findAll(any(Specification.class));
		verify(optionEntityRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void listUsesTtlFromConfigThenRefillsAfterExpiry() {
		cacheProperties.setCatalogTtl(Duration.ofMinutes(5));
		Product first = product("First", Instant.parse("2026-01-01T00:00:00Z"), ProductType.simple);
		Product second = product("Second", Instant.parse("2026-01-01T00:00:00Z"), ProductType.simple);
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(first), List.of(second));

		assertThat(((ProductResponse) service.list(null, "simple", null, 1, 10).items().getFirst()).getProductName())
				.isEqualTo("First");

		now.set(now.get().plus(Duration.ofMinutes(5)));
		assertThat(((ProductResponse) service.list(null, "simple", null, 1, 10).items().getFirst()).getProductName())
				.isEqualTo("Second");
		verify(productRepository, times(2)).findAll(any(Specification.class));
	}

	@Test
	void getProductHitAndMissingIsNotCached() {
		Product product = product("Pie", Instant.parse("2026-01-01T00:00:00Z"), ProductType.pizza);
		when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

		ProductResponse first = service.getProduct(product.getId());
		ProductResponse second = service.getProduct(product.getId());

		assertThat(first.getProductType()).isEqualTo("pizza-base");
		assertThat(second.getProductName()).isEqualTo("Pie");
		verify(productRepository, times(1)).findById(product.getId());
		assertThat(cacheStore.get(CatalogCacheKeys.product(product.getId()))).isPresent();

		UUID missing = UUID.randomUUID();
		when(productRepository.findById(missing)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getProduct(missing)).isInstanceOf(CatalogNotFoundException.class);
		assertThatThrownBy(() -> service.getProduct(missing)).isInstanceOf(CatalogNotFoundException.class);
		verify(productRepository, times(2)).findById(missing);
		assertThat(cacheStore.get(CatalogCacheKeys.product(missing))).isEmpty();
	}

	@Test
	void getOptionHitSkipsRepository() {
		OptionEntity option = option("Olive", Instant.parse("2026-01-01T00:00:00Z"));
		when(optionEntityRepository.findById(option.getId())).thenReturn(Optional.of(option));

		OptionResponse first = service.getOption(option.getId());
		OptionResponse second = service.getOption(option.getId());

		assertThat(first.getProductType()).isEqualTo("pizza-spec");
		assertThat(first.isBase()).isTrue();
		assertThat(second.getProductName()).isEqualTo("Olive");
		assertThat(second.isBase()).isTrue();
		verify(optionEntityRepository, times(1)).findById(option.getId());
	}

	@Test
	void unknownTypeDoesNotWriteCache() {
		assertThatThrownBy(() -> service.list(null, "dessert", null, 1, 10))
				.isInstanceOf(CatalogBadRequestException.class);
		assertThat(cacheStore.get(CatalogCacheKeys.list(null, "dessert", null, 1, 10))).isEmpty();
	}

	private static Product product(String name, Instant created, ProductType type) {
		Product product = new Product();
		product.setId(UUID.randomUUID());
		product.setName(name);
		product.setProductType(type);
		product.setCategory(ProductCategory.VEG);
		product.setPrice(new BigDecimal("99.00"));
		product.setActive(true);
		product.setCreatedAt(created);
		product.setUpdatedAt(created);
		return product;
	}

	private static OptionEntity option(String name, Instant created) {
		OptionEntity option = new OptionEntity();
		option.setId(UUID.randomUUID());
		option.setName(name);
		option.setKind(OptionKind.TOPPING);
		option.setPrice(new BigDecimal("15.00"));
		option.setBase(true);
		option.setCreatedAt(created);
		option.setUpdatedAt(created);
		return option;
	}
}
