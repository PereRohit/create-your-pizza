package com.createyourpizza.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

	private Duration catalogTtl = Duration.ofMinutes(3);
}
