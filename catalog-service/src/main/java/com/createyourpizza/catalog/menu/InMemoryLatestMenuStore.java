package com.createyourpizza.catalog.menu;

/**
 * In-process latest menu for tests and as fallback when Redis is not configured.
 */
public class InMemoryLatestMenuStore implements LatestMenuStore {

	private volatile LatestMenu latest;

	@Override
	public void put(LatestMenu menu) {
		this.latest = menu;
	}

	public LatestMenu getLatest() {
		return latest;
	}

	public void clear() {
		latest = null;
	}
}
