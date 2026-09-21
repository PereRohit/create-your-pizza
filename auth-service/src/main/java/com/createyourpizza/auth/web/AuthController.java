package com.createyourpizza.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.auth.service.AuthService;
import com.createyourpizza.auth.web.dto.AccessTokenData;
import com.createyourpizza.auth.web.dto.LoginRequest;
import com.createyourpizza.auth.web.dto.RegisterData;
import com.createyourpizza.auth.web.dto.RegisterRequest;
import com.createyourpizza.auth.web.dto.TokenRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/auth/login")
	public ResponseEntity<ApiEnvelope<AccessTokenData>> login(@Valid @RequestBody LoginRequest request) {
		AccessTokenData data = authService.login(request.username(), request.password());
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), data));
	}

	@PostMapping("/auth/register")
	public ResponseEntity<ApiEnvelope<RegisterData>> register(@Valid @RequestBody RegisterRequest request) {
		RegisterData data = authService.register(request.displayName());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiEnvelope.success(HttpStatus.CREATED.value(), data));
	}

	@PostMapping("/auth/token")
	public ResponseEntity<ApiEnvelope<AccessTokenData>> token(@Valid @RequestBody TokenRequest request) {
		AccessTokenData data = authService.exchangeToken(request.apiKey(), request.apiSecret());
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), data));
	}
}
