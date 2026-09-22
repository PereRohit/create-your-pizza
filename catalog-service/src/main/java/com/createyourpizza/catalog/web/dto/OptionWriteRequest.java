package com.createyourpizza.catalog.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OptionWriteRequest {

	@NotBlank
	private String optionKind;

	@NotBlank
	private String productName;

	@NotNull
	@PositiveOrZero
	private BigDecimal productPrice;

	@NotNull
	private Boolean isBase;
}
