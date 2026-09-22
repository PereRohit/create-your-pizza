package com.createyourpizza.catalog.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OptionResponse {

	UUID productId;
	String productName;
	String productType;
	BigDecimal productPrice;
	String optionKind;
	@JsonProperty("isBase")
	boolean isBase;
	Instant productCreatedAt;
	Instant productUpdatedAt;
}
