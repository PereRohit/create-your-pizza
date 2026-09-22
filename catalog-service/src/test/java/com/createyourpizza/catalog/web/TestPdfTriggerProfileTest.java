package com.createyourpizza.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.createyourpizza.catalog.service.PdfGenerationService;

class TestPdfTriggerProfileTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TestPdfTriggerController.class, StubPdfJobConfig.class);

	@Test
	void registeredOnTestProfile() {
		runner.withPropertyValues("spring.profiles.active=test")
				.run(context -> assertThat(context).hasSingleBean(TestPdfTriggerController.class));
	}

	@Test
	void registeredOnDevProfile() {
		runner.withPropertyValues("spring.profiles.active=dev")
				.run(context -> assertThat(context).hasSingleBean(TestPdfTriggerController.class));
	}

	@Test
	void notRegisteredOnDefaultProfile() {
		runner.run(context -> assertThat(context).doesNotHaveBean(TestPdfTriggerController.class));
	}

	@Test
	void notRegisteredOnProdProfile() {
		runner.withPropertyValues("spring.profiles.active=prod")
				.run(context -> assertThat(context).doesNotHaveBean(TestPdfTriggerController.class));
	}

	@Configuration
	static class StubPdfJobConfig {

		@Bean
		PdfGenerationService pdfGenerationService() {
			return new PdfGenerationService(
					null, null, null, null, null, null, null, null, null, null) {
				@Override
				public void runOnce() {
					// unused — profile tests only check bean registration
				}
			};
		}
	}
}
