package com.createyourpizza.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PdfPropertiesTest {

	@Test
	void defaultIntervalIsFiveMinutes() {
		assertThat(new PdfProperties().getInterval()).isEqualTo(Duration.ofMinutes(5));
	}
}
