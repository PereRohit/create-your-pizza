package com.createyourpizza.catalog.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OptionResponse {

	UUID productId;
	String productName;
	String productType;
	BigDecimal productPrice;
	String optionKind;
	@JsonProperty("isBase")
	@JsonAlias("base")
	boolean isBase;
	Instant productCreatedAt;
	Instant productUpdatedAt;
}
