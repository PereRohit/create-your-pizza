package com.createyourpizza.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CatalogDomainMappingTest {

	@Test
	void comboItemIdEqualityUsesBothKeys() {
		UUID combo = UUID.fromString("a1000000-0000-4000-8000-000000000003");
		UUID simple = UUID.fromString("a1000000-0000-4000-8000-000000000001");
		ComboItemId a = new ComboItemId(combo, simple);
		ComboItemId b = new ComboItemId(combo, simple);
		ComboItemId c = new ComboItemId(combo, UUID.fromString("a1000000-0000-4000-8000-000000000002"));

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);

		ComboItem item = new ComboItem(combo, simple);
		assertEquals(combo, item.getComboId());
		assertEquals(simple, item.getSimpleId());
	}

	@Test
	void productAndOptionEntityHoldDesignFields() {
		Product product = new Product();
		product.setName("Margherita Pizza");
		product.setProductType(ProductType.pizza);
		product.setCategory(ProductCategory.VEG);
		product.setPrice(new BigDecimal("299.00"));
		product.setOptionsEnabled(true);
		product.setCustomisationNotes("Extra basil on request");
		product.setActive(true);

		assertEquals(ProductType.pizza, product.getProductType());
		assertEquals(ProductCategory.VEG, product.getCategory());
		assertEquals(true, product.getOptionsEnabled());

		OptionEntity option = new OptionEntity();
		option.setKind(OptionKind.CRUST_TYPE);
		option.setName("thin crust");
		option.setPrice(new BigDecimal("10.00"));
		option.setBase(true);
		assertEquals(OptionKind.CRUST_TYPE, option.getKind());
		assertEquals(true, option.isBase());

		MenuPdf pdf = new MenuPdf();
		pdf.setVersion(1);
		pdf.setPdf(new byte[] { 1, 2 });
		pdf.setGeneratedAt(Instant.parse("2026-01-01T00:00:00Z"));
		assertEquals(1, pdf.getVersion());
		assertEquals(2, pdf.getPdf().length);
	}
}
