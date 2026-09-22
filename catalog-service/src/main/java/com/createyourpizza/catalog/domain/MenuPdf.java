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
@Table(name = "menu_pdf")
@Getter
@Setter
@NoArgsConstructor
public class MenuPdf {

	@Id
	private Integer version;

	@Column(nullable = false)
	private byte[] pdf;

	@Column(name = "generated_at", nullable = false)
	private Instant generatedAt;
}
