package com.createyourpizza.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	private String issuer = "create-your-pizza-auth";

	private String audience = "create-your-pizza-catalog";

	private Duration ttl = Duration.ofMinutes(30);
}
