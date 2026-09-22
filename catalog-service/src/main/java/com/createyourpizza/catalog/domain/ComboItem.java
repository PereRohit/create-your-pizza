package com.createyourpizza.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "combo_items")
@IdClass(ComboItemId.class)
@Getter
@Setter
@NoArgsConstructor
public class ComboItem {

	@Id
	@Column(name = "combo_id", nullable = false)
	private UUID comboId;

	@Id
	@Column(name = "simple_id", nullable = false)
	private UUID simpleId;

	public ComboItem(UUID comboId, UUID simpleId) {
		this.comboId = comboId;
		this.simpleId = simpleId;
	}
}
