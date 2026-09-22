package com.createyourpizza.catalog.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.catalog.service.CatalogWriteService;
import com.createyourpizza.catalog.web.dto.ProductResponse;
import com.createyourpizza.catalog.web.dto.ProductWriteRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductWriteController {

	private final CatalogWriteService catalogWriteService;

	@PostMapping
	public ResponseEntity<ApiEnvelope<ProductResponse>> create(@Valid @RequestBody ProductWriteRequest request) {
		ProductResponse created = catalogWriteService.createProduct(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiEnvelope.success(HttpStatus.CREATED.value(), created));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiEnvelope<ProductResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody ProductWriteRequest request) {
		ProductResponse updated = catalogWriteService.updateProduct(id, request);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiEnvelope<Void>> delete(@PathVariable UUID id) {
		catalogWriteService.deleteProduct(id);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), null));
	}
}
