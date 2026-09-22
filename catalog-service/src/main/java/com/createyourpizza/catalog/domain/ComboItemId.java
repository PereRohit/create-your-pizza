package com.createyourpizza.catalog.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ComboItemId implements Serializable {

	private UUID comboId;
	private UUID simpleId;

	public ComboItemId(UUID comboId, UUID simpleId) {
		this.comboId = comboId;
		this.simpleId = simpleId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ComboItemId that)) {
			return false;
		}
		return Objects.equals(comboId, that.comboId) && Objects.equals(simpleId, that.simpleId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(comboId, simpleId);
	}
}
