package com.createyourpizza.auth.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.auth.domain.UserRole;
import com.createyourpizza.auth.domain.UserStatus;
import com.createyourpizza.auth.service.AuthService;
import com.createyourpizza.auth.service.AuthService.UserListPage;
import com.createyourpizza.auth.web.dto.AccessTokenData;
import com.createyourpizza.auth.web.dto.AdminData;
import com.createyourpizza.auth.web.dto.ApproveData;
import com.createyourpizza.auth.web.dto.CreateAdminRequest;
import com.createyourpizza.auth.web.dto.LoginRequest;
import com.createyourpizza.auth.web.dto.RegisterData;
import com.createyourpizza.auth.web.dto.RegisterRequest;
import com.createyourpizza.auth.web.dto.TokenRequest;
import com.createyourpizza.auth.web.dto.UserListItem;
import com.createyourpizza.auth.web.dto.UserStatusData;

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

	@PostMapping("/auth/admins")
	public ResponseEntity<ApiEnvelope<AdminData>> createAdmin(@Valid @RequestBody CreateAdminRequest request) {
		AdminData data = authService.createAdmin(request.username(), request.password());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiEnvelope.success(HttpStatus.CREATED.value(), data));
	}

	@GetMapping("/auth/users")
	public ResponseEntity<ApiEnvelope<List<UserListItem>>> listUsers(
			@RequestParam(required = false) UserRole role,
			@RequestParam(required = false) UserStatus status,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		UserListPage result = authService.listUsers(role, status, page, size);
		return ResponseEntity.ok(ApiEnvelope.success(
				HttpStatus.OK.value(),
				result.items(),
				result.pagination()));
	}

	@PostMapping("/auth/users/{id}/approve")
	public ResponseEntity<ApiEnvelope<ApproveData>> approve(@PathVariable("id") UUID id) {
		ApproveData data = authService.approve(id);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), data));
	}

	@PostMapping("/auth/users/{id}/deny")
	public ResponseEntity<ApiEnvelope<UserStatusData>> deny(@PathVariable("id") UUID id) {
		UserStatusData data = authService.deny(id);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), data));
	}

	@PostMapping("/auth/users/{id}/revoke")
	public ResponseEntity<ApiEnvelope<UserStatusData>> revoke(@PathVariable("id") UUID id) {
		UserStatusData data = authService.revoke(id);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), data));
	}

	@DeleteMapping("/auth/users/{id}")
	public ResponseEntity<ApiEnvelope<Void>> deleteUser(
			@PathVariable("id") UUID id,
			Authentication authentication) {
		String callerSub = authentication != null ? String.valueOf(authentication.getPrincipal()) : null;
		authService.deleteUser(id, callerSub);
		return ResponseEntity.ok(ApiEnvelope.success(HttpStatus.OK.value(), null));
	}
}
