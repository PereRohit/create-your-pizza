package com.createyourpizza.catalog.pdf;

import java.math.BigDecimal;
import java.util.List;

public record MenuPdfModel(
		String header,
		int version,
		List<SellableRow> sellable,
		List<OptionRow> pizzaSpec) {

	public static final String HEADER = "Create Your Pizza";

	public static final String OPTIONS_AVAILABLE = "options available";

	public record SellableRow(String name, BigDecimal price, boolean optionsAvailable) {
	}

	public record OptionRow(String name, BigDecimal price) {
	}
}
