package com.createyourpizza.catalog.seed;

import java.time.Instant;

import com.createyourpizza.catalog.domain.CatalogMeta;

/**
 * Defaults for the singleton {@code catalog_meta} row (id=1) after V2 seed.
 */
public final class CatalogMetaDefaults {

	public static final short META_ID = 1;
	public static final boolean DIRTY = true;
	public static final int LAST_PDF_VERSION = 0;

	private CatalogMetaDefaults() {
	}

	public static CatalogMeta newSeedRow(Instant lastCatalogChangeAt) {
		CatalogMeta meta = new CatalogMeta();
		meta.setId(META_ID);
		meta.setDirty(DIRTY);
		meta.setLastCatalogChangeAt(lastCatalogChangeAt);
		meta.setLastPdfVersion(LAST_PDF_VERSION);
		return meta;
	}
}
