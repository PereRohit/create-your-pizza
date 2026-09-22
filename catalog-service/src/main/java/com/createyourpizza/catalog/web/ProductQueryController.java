package com.createyourpizza.catalog.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.catalog.service.CatalogQueryService;
import com.createyourpizza.catalog.service.CatalogQueryService.CatalogListPage;
import com.createyourpizza.catalog.web.dto.ProductResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductQueryController {

	private final CatalogQueryService catalogQueryService;

	@GetMapping
	public ResponseEntity<ApiEnvelope<List<Object>>> list(
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		CatalogListPage result = catalogQueryService.list(category, type, maxPrice, page, size);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), result.items(), result.pagination()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ApiEnvelope<ProductResponse>> get(@PathVariable UUID id) {
		ProductResponse product = catalogQueryService.getProduct(id);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), product));
	}
}
