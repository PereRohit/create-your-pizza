package com.createyourpizza.catalog.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process map + TTL clock for tests and as fallback when Redis is not configured.
 */
public class InMemoryCatalogCacheStore implements CatalogCacheStore {

	private final Clock clock;
	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	public InMemoryCatalogCacheStore(Clock clock) {
		this.clock = clock;
	}

	@Override
	public Optional<String> get(String key) {
		Entry entry = entries.get(key);
		if (entry == null) {
			return Optional.empty();
		}
		if (!entry.expiresAt.isAfter(clock.instant())) {
			entries.remove(key, entry);
			return Optional.empty();
		}
		return Optional.of(entry.json);
	}

	@Override
	public void put(String key, String json, Duration ttl) {
		entries.put(key, new Entry(json, clock.instant().plus(ttl)));
	}

	public void clear() {
		entries.clear();
	}

	private record Entry(String json, Instant expiresAt) {
	}
}
