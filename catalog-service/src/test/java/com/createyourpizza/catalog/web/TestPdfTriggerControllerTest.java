package com.createyourpizza.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.createyourpizza.catalog.service.PdfGenerationService;

class TestPdfTriggerControllerTest {

	@Test
	void generateDelegatesToSharedJobAndReturns202() {
		AtomicInteger runs = new AtomicInteger();
		PdfGenerationService pdfGenerationService = new PdfGenerationService(
				null, null, null, null, null, null, null, null, null, null) {
			@Override
			public void runOnce() {
				runs.incrementAndGet();
			}
		};
		TestPdfTriggerController controller = new TestPdfTriggerController(pdfGenerationService);

		ResponseEntity<ApiEnvelope<Void>> response = controller.generate();

		assertThat(runs.get()).isEqualTo(1);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(202);
		assertThat(response.getBody().message()).isEqualTo("success");
	}
}
