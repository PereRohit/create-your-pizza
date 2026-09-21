package com.createyourpizza.auth.web.dto;

import java.util.UUID;

import com.createyourpizza.auth.domain.UserStatus;

public record ApproveData(UUID userId, UserStatus status, String apiKey, String apiSecret) {
}
