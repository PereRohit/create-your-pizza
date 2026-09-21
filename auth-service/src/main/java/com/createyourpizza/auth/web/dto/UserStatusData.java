package com.createyourpizza.auth.web.dto;

import java.util.UUID;

import com.createyourpizza.auth.domain.UserRole;
import com.createyourpizza.auth.domain.UserStatus;

public record UserStatusData(UUID userId, UserRole role, UserStatus status) {
}
