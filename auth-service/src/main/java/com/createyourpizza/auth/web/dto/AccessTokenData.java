package com.createyourpizza.auth.web.dto;

public record AccessTokenData(String accessToken, String tokenType, long expiresIn) {
}
