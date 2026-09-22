package com.createyourpizza.catalog.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.lock.InMemoryCatalogLockStore;
import com.createyourpizza.catalog.lock.RedisCatalogLockStore;

@Configuration
@EnableConfigurationProperties(LockProperties.class)
public class CatalogLockConfiguration {

	/**
	 * Resolved at bean-creation time: {@code StringRedisTemplate} is contributed by
	 * auto-configuration, which runs after this application {@code @Configuration}.
	 */
	@Bean
	CatalogLockStore catalogLockStore(ObjectProvider<StringRedisTemplate> redis) {
		return redis.stream().findFirst()
				.<CatalogLockStore>map(RedisCatalogLockStore::new)
				.orElseGet(InMemoryCatalogLockStore::new);
	}
}
