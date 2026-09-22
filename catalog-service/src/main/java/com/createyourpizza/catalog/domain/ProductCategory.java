package com.createyourpizza.catalog.domain;

public enum ProductCategory {
	VEG("veg"),
	NON_VEG("non-veg");

	private final String dbValue;

	ProductCategory(String dbValue) {
		this.dbValue = dbValue;
	}

	public String getDbValue() {
		return dbValue;
	}

	public static ProductCategory fromDbValue(String value) {
		for (ProductCategory category : values()) {
			if (category.dbValue.equals(value)) {
				return category;
			}
		}
		throw new IllegalArgumentException("Unknown product category: " + value);
	}
}
