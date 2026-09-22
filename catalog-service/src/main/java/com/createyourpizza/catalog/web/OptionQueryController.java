package com.createyourpizza.catalog.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.catalog.service.CatalogQueryService;
import com.createyourpizza.catalog.web.dto.OptionResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/options")
@RequiredArgsConstructor
public class OptionQueryController {

	private final CatalogQueryService catalogQueryService;

	@GetMapping("/{id}")
	public ResponseEntity<ApiEnvelope<OptionResponse>> get(@PathVariable UUID id) {
		OptionResponse option = catalogQueryService.getOption(id);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), option));
	}
}
