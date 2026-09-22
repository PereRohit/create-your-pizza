package com.createyourpizza.catalog.menu;

/**
 * Latest menu Redis key (Design §7). No TTL — the PDF job replaces the value.
 * GET is story 13.
 */
public interface LatestMenuStore {

	void put(LatestMenu menu);
}
