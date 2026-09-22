package com.createyourpizza.catalog.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.createyourpizza.catalog.cache.CatalogCacheStore;
import com.createyourpizza.catalog.cache.InMemoryCatalogCacheStore;
import com.createyourpizza.catalog.cache.RedisCatalogCacheStore;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CatalogCacheConfiguration {

	@Bean
	@ConditionalOnBean(StringRedisTemplate.class)
	CatalogCacheStore redisCatalogCacheStore(StringRedisTemplate redis) {
		return new RedisCatalogCacheStore(redis);
	}

	@Bean
	@ConditionalOnMissingBean(CatalogCacheStore.class)
	CatalogCacheStore inMemoryCatalogCacheStore() {
		return new InMemoryCatalogCacheStore(Clock.systemUTC());
	}
}
