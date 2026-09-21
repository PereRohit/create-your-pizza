package com.createyourpizza.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Trusted-system register body. Any client-supplied {@code role} is ignored
 * (field is not bound); role is always forced to TRUSTED_SYSTEM server-side.
 */
public record RegisterRequest(@NotBlank String displayName) {
}
