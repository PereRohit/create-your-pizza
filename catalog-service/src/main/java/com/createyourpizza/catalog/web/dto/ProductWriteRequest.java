package com.createyourpizza.catalog.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductWriteRequest {

	@NotBlank
	private String productName;

	@NotBlank
	private String productType;

	@NotBlank
	private String productCategory;

	@NotNull
	@PositiveOrZero
	private BigDecimal productPrice;

	private Boolean optionsEnabled;

	private String customisationNotes;

	private Boolean active = true;

	/** Simple product ids when {@code productType} is {@code combo}. */
	private List<UUID> simpleIds;
}
