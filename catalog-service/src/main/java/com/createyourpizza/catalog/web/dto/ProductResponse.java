package com.createyourpizza.catalog.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {

	UUID productId;
	String productName;
	String productType;
	String productCategory;
	BigDecimal productPrice;
	Boolean optionsEnabled;
	String customisationNotes;
	boolean active;
	List<UUID> simpleIds;
	Instant productCreatedAt;
	Instant productUpdatedAt;
}
