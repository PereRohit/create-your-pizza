package com.createyourpizza.catalog.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.createyourpizza.catalog.domain.OptionKind;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;

class CatalogSeedExpectationsTest {

	@Test
	void optionEntitiesCoverFr4aThroughFr4cWithPerRowPrices() {
		// Locked Spec FR-4a–c contract (same values as V2__catalog_seed.sql)
		List<CatalogSeedExpectations.OptionSeed> expected = List.of(
				new CatalogSeedExpectations.OptionSeed(OptionKind.CRUST_SIZE, "10 inch (small)",
						new BigDecimal("0.00"), true),
				new CatalogSeedExpectations.OptionSeed(OptionKind.CRUST_SIZE, "12 inch (medium)",
						new BigDecimal("40.00"), false),
				new CatalogSeedExpectations.OptionSeed(OptionKind.CRUST_SIZE, "15 inch (large)",
						new BigDecimal("80.00"), false),
				new CatalogSeedExpectations.OptionSeed(OptionKind.CRUST_TYPE, "thin crust",
						new BigDecimal("10.00"), true),
				new CatalogSeedExpectations.OptionSeed(OptionKind.CRUST_TYPE, "cheese burst",
						new BigDecimal("20.00"), false),
				new CatalogSeedExpectations.OptionSeed(OptionKind.CRUST_TYPE, "deep dish",
						new BigDecimal("25.00"), false),
				new CatalogSeedExpectations.OptionSeed(OptionKind.TOPPING, "olive", new BigDecimal("15.00"), true),
				new CatalogSeedExpectations.OptionSeed(OptionKind.TOPPING, "chicken", new BigDecimal("35.00"), false),
				new CatalogSeedExpectations.OptionSeed(OptionKind.TOPPING, "mushrooms", new BigDecimal("25.00"),
						false),
				new CatalogSeedExpectations.OptionSeed(OptionKind.TOPPING, "pepperoni", new BigDecimal("40.00"),
						false));

		assertEquals(expected.size(), CatalogSeedExpectations.OPTION_ENTITIES.size());
		assertEquals(expected, CatalogSeedExpectations.OPTION_ENTITIES);

		for (CatalogSeedExpectations.OptionSeed option : expected) {
			CatalogSeedExpectations.OptionSeed actual = CatalogSeedExpectations.OPTION_ENTITIES.stream()
					.filter(o -> o.name().equals(option.name()))
					.findFirst()
					.orElseThrow(() -> new AssertionError("Missing option: " + option.name()));
			assertEquals(option.kind(), actual.kind(), option.name());
			assertEquals(option.price(), actual.price(), option.name());
			assertEquals(option.base(), actual.base(), option.name());
		}

		assertEquals("10 inch (small)", CatalogSeedExpectations.requireBase(OptionKind.CRUST_SIZE).name());
		assertEquals("thin crust", CatalogSeedExpectations.requireBase(OptionKind.CRUST_TYPE).name());
		assertEquals("olive", CatalogSeedExpectations.requireBase(OptionKind.TOPPING).name());
		assertEquals(1, CatalogSeedExpectations.basesOf(OptionKind.CRUST_SIZE).size());
		assertEquals(1, CatalogSeedExpectations.basesOf(OptionKind.CRUST_TYPE).size());
		assertEquals(1, CatalogSeedExpectations.basesOf(OptionKind.TOPPING).size());
	}

	@Test
	void productsIncludeSimpleComboAndPizzaWithOptionsEnabledOnPizzaOnly() {
		List<CatalogSeedExpectations.ProductSeed> products = CatalogSeedExpectations.PRODUCTS;
		assertEquals(4, products.size());
		assertTrue(products.stream().anyMatch(p -> p.type() == ProductType.simple));
		assertTrue(products.stream().anyMatch(p -> p.type() == ProductType.combo));
		assertTrue(products.stream().anyMatch(p -> p.type() == ProductType.pizza));

		CatalogSeedExpectations.ProductSeed pizza = products.stream()
				.filter(p -> p.type() == ProductType.pizza)
				.findFirst()
				.orElseThrow();
		assertTrue(pizza.optionsEnabled());
		assertEquals(ProductCategory.VEG, pizza.category());

		assertTrue(products.stream()
				.filter(p -> p.type() != ProductType.pizza)
				.noneMatch(p -> Boolean.TRUE.equals(p.optionsEnabled())));
		assertEquals(2, CatalogSeedExpectations.COMBO_MEMBER_COUNT);
	}
}
