package com.createyourpizza.catalog.cache;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisCatalogCacheStore implements CatalogCacheStore {

	private final StringRedisTemplate redis;

	public RedisCatalogCacheStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public Optional<String> get(String key) {
		return Optional.ofNullable(redis.opsForValue().get(key));
	}

	@Override
	public void put(String key, String json, Duration ttl) {
		redis.opsForValue().set(key, json, ttl);
	}
}
