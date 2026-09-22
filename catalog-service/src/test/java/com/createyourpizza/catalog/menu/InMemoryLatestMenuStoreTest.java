package com.createyourpizza.catalog.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class InMemoryLatestMenuStoreTest {

	@Test
	void putReplacesAndClearRemoves() {
		InMemoryLatestMenuStore store = new InMemoryLatestMenuStore();
		LatestMenu first = new LatestMenu(new byte[] { 1 }, 1, Instant.parse("2026-01-01T00:00:00Z"));
		LatestMenu second = new LatestMenu(new byte[] { 2 }, 2, Instant.parse("2026-01-01T00:05:00Z"));

		store.put(first);
		assertThat(store.get()).contains(first);
		assertThat(store.getLatest()).isSameAs(first);
		store.put(second);
		assertThat(store.get()).contains(second);
		assertThat(store.getLatest()).isSameAs(second);
		store.clear();
		assertThat(store.get()).isEmpty();
		assertThat(store.getLatest()).isNull();
	}
}
