package com.createyourpizza.catalog.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "catalog_meta")
@Getter
@Setter
@NoArgsConstructor
public class CatalogMeta {

	@Id
	private Short id;

	@Column(nullable = false)
	private boolean dirty;

	@Column(name = "last_catalog_change_at", nullable = false)
	private Instant lastCatalogChangeAt;

	@Column(name = "last_pdf_version", nullable = false)
	private int lastPdfVersion;
}
