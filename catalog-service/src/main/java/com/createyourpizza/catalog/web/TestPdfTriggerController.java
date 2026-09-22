package com.createyourpizza.catalog.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.catalog.service.PdfGenerationService;

import lombok.RequiredArgsConstructor;

@RestController
@Profile({"test", "dev"})
@RequiredArgsConstructor
public class TestPdfTriggerController {

	private final PdfGenerationService pdfGenerationService;

	@PostMapping("/test/pdf/generate")
	public ResponseEntity<ApiEnvelope<Void>> generate() {
		pdfGenerationService.runOnce();
		return ResponseEntity.accepted().body(ApiEnvelope.success(HttpStatus.ACCEPTED.value(), null));
	}
}
