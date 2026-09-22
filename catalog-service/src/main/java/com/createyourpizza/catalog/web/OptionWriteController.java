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
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.OptionWriteRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/options")
@RequiredArgsConstructor
public class OptionWriteController {

	private final CatalogWriteService catalogWriteService;

	@PostMapping
	public ResponseEntity<ApiEnvelope<OptionResponse>> create(@Valid @RequestBody OptionWriteRequest request) {
		OptionResponse created = catalogWriteService.createOption(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiEnvelope.success(HttpStatus.CREATED.value(), created));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiEnvelope<OptionResponse>> update(
			@PathVariable UUID id,
			@Valid @RequestBody OptionWriteRequest request) {
		OptionResponse updated = catalogWriteService.updateOption(id, request);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiEnvelope<Void>> delete(@PathVariable UUID id) {
		catalogWriteService.deleteOption(id);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), null));
	}
}
