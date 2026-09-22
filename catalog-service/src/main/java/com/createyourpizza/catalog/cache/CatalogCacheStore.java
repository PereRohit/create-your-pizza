package com.createyourpizza.catalog.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Catalog list/detail JSON cache port (Design §7). Tests supply an in-memory map.
 */
public interface CatalogCacheStore {

	Optional<String> get(String key);

	void put(String key, String json, Duration ttl);
}
