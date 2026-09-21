package com.createyourpizza.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
		@NotBlank String apiKey,
		@NotBlank String apiSecret) {
}
