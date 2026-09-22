package com.createyourpizza.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.pdf")
public class PdfProperties {

	private Duration interval = Duration.ofMinutes(5);
}
