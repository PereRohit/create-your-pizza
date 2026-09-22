package com.createyourpizza.catalog.lock;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisCatalogLockStore implements CatalogLockStore {

	private final StringRedisTemplate redis;

	public RedisCatalogLockStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public boolean isHeld(String key) {
		return Boolean.TRUE.equals(redis.hasKey(key));
	}

	@Override
	public boolean tryAcquire(String key, String holder, Duration ttl) {
		Boolean acquired = redis.opsForValue().setIfAbsent(key, holder, ttl);
		return Boolean.TRUE.equals(acquired);
	}

	@Override
	public void release(String key) {
		redis.delete(key);
	}
}
