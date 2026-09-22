package com.createyourpizza.catalog.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.createyourpizza.catalog.domain.CatalogMeta;

class CatalogMetaDefaultsTest {

	@Test
	void seedRowUsesSingletonIdDirtyTrueAndZeroLastPdfVersion() {
		Instant at = Instant.parse("2026-01-01T00:00:00Z");
		CatalogMeta meta = CatalogMetaDefaults.newSeedRow(at);

		assertEquals(CatalogMetaDefaults.META_ID, meta.getId());
		assertTrue(meta.isDirty());
		assertEquals(CatalogMetaDefaults.DIRTY, meta.isDirty());
		assertEquals(CatalogMetaDefaults.LAST_PDF_VERSION, meta.getLastPdfVersion());
		assertEquals(0, meta.getLastPdfVersion());
		assertEquals(at, meta.getLastCatalogChangeAt());
	}
}
