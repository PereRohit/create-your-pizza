package com.createyourpizza.catalog.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.catalog.service.MenuPdfQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MenuPdfController {

	private final MenuPdfQueryService menuPdfQueryService;

	@GetMapping("/api/menu.pdf")
	public ResponseEntity<byte[]> get(@RequestParam(required = false) Integer version) {
		byte[] pdf = menuPdfQueryService.getPdf(version);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf);
	}
}
