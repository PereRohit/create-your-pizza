package com.createyourpizza.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.createyourpizza.catalog.cache.CatalogCacheStore;
import com.createyourpizza.catalog.cache.RedisCatalogCacheStore;
import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.lock.RedisCatalogLockStore;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.menu.RedisLatestMenuStore;

/**
 * Same regression guard as {@link CatalogStoreWiringTest}, but on the whole application
 * context. The shared {@code src/test/resources/application.properties} excludes
 * {@code DataRedisAutoConfiguration} — which is what hid BUG-01 — so this test re-declares
 * {@code spring.autoconfigure.exclude} without it, for this context only. Lettuce connects
 * lazily, so no Redis server is needed to assert which implementations were wired.
 */
@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
				+ "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration" })
class CatalogRedisWiringApplicationTest {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Autowired
	private CatalogCacheStore cacheStore;

	@Autowired
	private CatalogLockStore lockStore;

	@Autowired
	private LatestMenuStore latestMenuStore;

	@Test
	void everyRedisBackedPortIsWiredToItsRedisImplementation() {
		assertThat(stringRedisTemplate).isNotNull();
		assertThat(cacheStore).isInstanceOf(RedisCatalogCacheStore.class);
		assertThat(lockStore).isInstanceOf(RedisCatalogLockStore.class);
		assertThat(latestMenuStore).isInstanceOf(RedisLatestMenuStore.class);
	}
}
