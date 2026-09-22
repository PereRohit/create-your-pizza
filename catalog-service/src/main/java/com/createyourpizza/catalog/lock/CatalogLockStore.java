package com.createyourpizza.catalog.lock;

import java.time.Duration;

/**
 * Redis lock port (Design §6). Tests supply an in-memory fake.
 */
public interface CatalogLockStore {

	boolean isHeld(String key);

	boolean tryAcquire(String key, String holder, Duration ttl);

	void release(String key);
}
