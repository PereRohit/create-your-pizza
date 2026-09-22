package com.createyourpizza.catalog.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PdfGenerationScheduler {

	private final PdfGenerationService pdfGenerationService;

	@Scheduled(
			initialDelayString = "#{@pdfProperties.interval.toMillis()}",
			fixedDelayString = "#{@pdfProperties.interval.toMillis()}")
	public void generate() {
		pdfGenerationService.runOnce();
	}
}
