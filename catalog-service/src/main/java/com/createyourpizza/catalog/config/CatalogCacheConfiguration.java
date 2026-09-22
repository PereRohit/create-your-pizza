package com.createyourpizza.catalog.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
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

	/**
	 * Resolved at bean-creation time: {@code StringRedisTemplate} is contributed by
	 * auto-configuration, which runs after this application {@code @Configuration}.
	 */
	@Bean
	CatalogCacheStore catalogCacheStore(ObjectProvider<StringRedisTemplate> redis) {
		return redis.stream().findFirst()
				.<CatalogCacheStore>map(RedisCatalogCacheStore::new)
				.orElseGet(() -> new InMemoryCatalogCacheStore(Clock.systemUTC()));
	}
}
