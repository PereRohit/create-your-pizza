package com.createyourpizza.catalog.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process lock map for tests and as fallback when Redis is not configured.
 */
public class InMemoryCatalogLockStore implements CatalogLockStore {

	private final Map<String, Instant> expiryByKey = new ConcurrentHashMap<>();

	@Override
	public boolean isHeld(String key) {
		purgeExpired(key);
		return expiryByKey.containsKey(key);
	}

	@Override
	public boolean tryAcquire(String key, String holder, Duration ttl) {
		purgeExpired(key);
		Instant expiry = Instant.now().plus(ttl);
		Instant previous = expiryByKey.putIfAbsent(key, expiry);
		return previous == null;
	}

	@Override
	public void release(String key) {
		expiryByKey.remove(key);
	}

	private void purgeExpired(String key) {
		Instant expiry = expiryByKey.get(key);
		if (expiry != null && !expiry.isAfter(Instant.now())) {
			expiryByKey.remove(key, expiry);
		}
	}
}
