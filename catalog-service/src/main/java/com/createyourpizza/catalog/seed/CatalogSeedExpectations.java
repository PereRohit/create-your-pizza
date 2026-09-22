package com.createyourpizza.catalog.seed;

import java.math.BigDecimal;
import java.util.List;

import com.createyourpizza.catalog.domain.OptionKind;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;

/**
 * Flyway-independent contract for V2 catalog seed (Spec FR-4a–c + Design sample products).
 */
public final class CatalogSeedExpectations {

	public record OptionSeed(OptionKind kind, String name, BigDecimal price, boolean base) {
	}

	public record ProductSeed(String name, ProductType type, ProductCategory category, BigDecimal price,
			Boolean optionsEnabled) {
	}

	public static final List<OptionSeed> OPTION_ENTITIES = List.of(
			new OptionSeed(OptionKind.CRUST_SIZE, "10 inch (small)", new BigDecimal("0.00"), true),
			new OptionSeed(OptionKind.CRUST_SIZE, "12 inch (medium)", new BigDecimal("40.00"), false),
			new OptionSeed(OptionKind.CRUST_SIZE, "15 inch (large)", new BigDecimal("80.00"), false),
			new OptionSeed(OptionKind.CRUST_TYPE, "thin crust", new BigDecimal("10.00"), true),
			new OptionSeed(OptionKind.CRUST_TYPE, "cheese burst", new BigDecimal("20.00"), false),
			new OptionSeed(OptionKind.CRUST_TYPE, "deep dish", new BigDecimal("25.00"), false),
			new OptionSeed(OptionKind.TOPPING, "olive", new BigDecimal("15.00"), true),
			new OptionSeed(OptionKind.TOPPING, "chicken", new BigDecimal("35.00"), false),
			new OptionSeed(OptionKind.TOPPING, "mushrooms", new BigDecimal("25.00"), false),
			new OptionSeed(OptionKind.TOPPING, "pepperoni", new BigDecimal("40.00"), false));

	public static final List<ProductSeed> PRODUCTS = List.of(
			new ProductSeed("Garlic Bread", ProductType.simple, ProductCategory.VEG, new BigDecimal("80.00"), false),
			new ProductSeed("Chicken Wings", ProductType.simple, ProductCategory.NON_VEG, new BigDecimal("150.00"),
					false),
			new ProductSeed("Snack Combo", ProductType.combo, ProductCategory.NON_VEG, new BigDecimal("199.00"), false),
			new ProductSeed("Margherita Pizza", ProductType.pizza, ProductCategory.VEG, new BigDecimal("299.00"), true));

	/** Combo "Snack Combo" membership count in V2 seed. */
	public static final int COMBO_MEMBER_COUNT = 2;

	private CatalogSeedExpectations() {
	}

	public static List<OptionSeed> basesOf(OptionKind kind) {
		return OPTION_ENTITIES.stream().filter(o -> o.kind() == kind && o.base()).toList();
	}

	public static OptionSeed requireBase(OptionKind kind) {
		List<OptionSeed> bases = basesOf(kind);
		if (bases.size() != 1) {
			throw new IllegalStateException("Expected exactly one base for " + kind + ", got " + bases.size());
		}
		return bases.getFirst();
	}
}
