package com.createyourpizza.catalog.menu;

import java.util.Optional;

/**
 * In-process latest menu for tests and as fallback when Redis is not configured.
 */
public class InMemoryLatestMenuStore implements LatestMenuStore {

	private volatile LatestMenu latest;

	@Override
	public void put(LatestMenu menu) {
		this.latest = menu;
	}

	@Override
	public Optional<LatestMenu> get() {
		return Optional.ofNullable(latest);
	}

	public LatestMenu getLatest() {
		return latest;
	}

	public void clear() {
		latest = null;
	}
}
