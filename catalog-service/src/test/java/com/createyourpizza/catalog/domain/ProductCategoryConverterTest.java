package com.createyourpizza.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductCategoryConverterTest {

	private final ProductCategoryConverter converter = new ProductCategoryConverter();

	@Test
	void roundTripsVegAndNonVegDbValues() {
		assertEquals("veg", converter.convertToDatabaseColumn(ProductCategory.VEG));
		assertEquals("non-veg", converter.convertToDatabaseColumn(ProductCategory.NON_VEG));
		assertEquals(ProductCategory.VEG, converter.convertToEntityAttribute("veg"));
		assertEquals(ProductCategory.NON_VEG, converter.convertToEntityAttribute("non-veg"));
	}

	@Test
	void nullSafe() {
		assertNull(converter.convertToDatabaseColumn(null));
		assertNull(converter.convertToEntityAttribute(null));
	}

	@Test
	void rejectsUnknownDbValue() {
		assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute("keto"));
	}
}
