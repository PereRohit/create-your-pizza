package com.createyourpizza.catalog.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

	@Bean
	@ConditionalOnBean(StringRedisTemplate.class)
	CatalogLockStore redisCatalogLockStore(StringRedisTemplate redis) {
		return new RedisCatalogLockStore(redis);
	}

	@Bean
	@ConditionalOnMissingBean(CatalogLockStore.class)
	CatalogLockStore inMemoryCatalogLockStore() {
		return new InMemoryCatalogLockStore();
	}
}
