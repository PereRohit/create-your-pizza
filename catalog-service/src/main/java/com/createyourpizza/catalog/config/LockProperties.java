package com.createyourpizza.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.lock")
public class LockProperties {

	private Duration pdfTtl = Duration.ofSeconds(120);

	private Duration writeTtl = Duration.ofSeconds(30);
}
