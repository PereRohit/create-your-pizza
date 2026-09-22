package com.createyourpizza.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class InMemoryCatalogCacheStoreTest {

	@Test
	void getMissAndPutThenExpireAtTtl() {
		AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
		Clock clock = new Clock() {
			@Override
			public ZoneOffset getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now.get();
			}
		};
		InMemoryCatalogCacheStore store = new InMemoryCatalogCacheStore(clock);

		assertThat(store.get("create-your-pizza/catalog:list:|| |1|10")).isEmpty();
		store.put("create-your-pizza/catalog:product:1", "{\"ok\":true}", Duration.ofMinutes(3));
		assertThat(store.get("create-your-pizza/catalog:product:1")).contains("{\"ok\":true}");

		now.set(now.get().plus(Duration.ofMinutes(3)));
		assertThat(store.get("create-your-pizza/catalog:product:1")).isEmpty();
	}

	@Test
	void clearRemovesEntries() {
		InMemoryCatalogCacheStore store = new InMemoryCatalogCacheStore(Clock.systemUTC());
		store.put("create-your-pizza/catalog:option:1", "{}", Duration.ofMinutes(3));
		store.clear();
		assertThat(store.get("create-your-pizza/catalog:option:1")).isEmpty();
	}
}
