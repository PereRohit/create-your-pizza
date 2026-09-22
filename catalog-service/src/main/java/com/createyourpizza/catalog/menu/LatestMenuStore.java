package com.createyourpizza.catalog.menu;

import java.util.Optional;

/**
 * Latest menu Redis key (Design §7). No TTL — the PDF job replaces the value.
 */
public interface LatestMenuStore {

	void put(LatestMenu menu);

	Optional<LatestMenu> get();
}
