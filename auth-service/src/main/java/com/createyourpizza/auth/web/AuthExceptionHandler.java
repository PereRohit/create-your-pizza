package com.createyourpizza.auth.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.createyourpizza.auth.service.AuthFailureException;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler(AuthFailureException.class)
	public ResponseEntity<ApiEnvelope<Void>> handleAuthFailure(AuthFailureException ex) {
		HttpStatus status = HttpStatus.UNAUTHORIZED;
		String reason = ex.getReason() != null ? ex.getReason() : "Unauthorized";
		return ResponseEntity.status(status)
				.body(ApiEnvelope.error(status.value(), reason, reason));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiEnvelope<Void>> handleResponseStatus(ResponseStatusException ex) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
		return ResponseEntity.status(status)
				.body(ApiEnvelope.error(status.value(), reason, reason));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiEnvelope<Void>> handleValidation(MethodArgumentNotValidException ex) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		String message = "Validation failed";
		return ResponseEntity.status(status)
				.body(ApiEnvelope.error(status.value(), message, message));
	}
}
