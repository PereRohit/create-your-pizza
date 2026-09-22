package com.createyourpizza.catalog.cache;

import java.math.BigDecimal;
import java.util.UUID;

public final class CatalogCacheKeys {

	public static final String PREFIX = "create-your-pizza/catalog:";

	private CatalogCacheKeys() {
	}

	public static String list(String category, String type, BigDecimal maxPrice, int page, int size) {
		String price = maxPrice == null ? "" : maxPrice.stripTrailingZeros().toPlainString();
		return PREFIX + "list:"
				+ empty(category) + "|"
				+ empty(type) + "|"
				+ price + "|"
				+ page + "|"
				+ size;
	}

	public static String product(UUID id) {
		return PREFIX + "product:" + id;
	}

	public static String option(UUID id) {
		return PREFIX + "option:" + id;
	}

	private static String empty(String value) {
		return value == null ? "" : value;
	}
}
