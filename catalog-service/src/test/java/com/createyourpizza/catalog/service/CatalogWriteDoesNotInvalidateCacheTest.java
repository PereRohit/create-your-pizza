package com.createyourpizza.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.createyourpizza.catalog.cache.CatalogCacheKeys;
import com.createyourpizza.catalog.cache.CatalogCacheStore;
import com.createyourpizza.catalog.cache.InMemoryCatalogCacheStore;
import com.createyourpizza.catalog.config.LockProperties;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.lock.InMemoryCatalogLockStore;
import com.createyourpizza.catalog.repository.CatalogMetaRepository;
import com.createyourpizza.catalog.repository.ComboItemRepository;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.seed.CatalogMetaDefaults;
import com.createyourpizza.catalog.web.dto.ProductWriteRequest;

@ExtendWith(MockitoExtension.class)
class CatalogWriteDoesNotInvalidateCacheTest {

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ComboItemRepository comboItemRepository;

	@Mock
	private OptionEntityRepository optionEntityRepository;

	@Mock
	private CatalogMetaRepository catalogMetaRepository;

	@Test
	void writeServiceHasNoCachePortAndDoesNotDeleteCatalogKeys() {
		assertThat(Arrays.stream(CatalogWriteService.class.getDeclaredFields())
				.map(Field::getType)
				.noneMatch(CatalogCacheStore.class::equals)).isTrue();

		InMemoryCatalogCacheStore cache = new InMemoryCatalogCacheStore(Clock.systemUTC());
		String key = CatalogCacheKeys.product(UUID.randomUUID());
		cache.put(key, "{\"stale\":true}", Duration.ofMinutes(3));

		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		});
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product product = invocation.getArgument(0);
			product.setId(UUID.randomUUID());
			product.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
			product.setUpdatedAt(product.getCreatedAt());
			return product;
		});
		when(catalogMetaRepository.findById(CatalogMetaDefaults.META_ID))
				.thenReturn(Optional.of(CatalogMetaDefaults.newSeedRow(Instant.parse("2026-01-01T00:00:00Z"))));

		CatalogWriteService writes = new CatalogWriteService(
				new InMemoryCatalogLockStore(),
				new LockProperties(),
				transactionTemplate,
				productRepository,
				comboItemRepository,
				optionEntityRepository,
				catalogMetaRepository,
				new CatalogResponseMapper(comboItemRepository));

		ProductWriteRequest request = new ProductWriteRequest();
		request.setProductName("New Simple");
		request.setProductType("simple");
		request.setProductCategory("veg");
		request.setProductPrice(new BigDecimal("10.00"));
		writes.createProduct(request);

		assertThat(cache.get(key)).contains("{\"stale\":true}");
	}
}
