package com.createyourpizza.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAdminRequest(
		@NotBlank String username,
		@NotBlank String password) {
}
