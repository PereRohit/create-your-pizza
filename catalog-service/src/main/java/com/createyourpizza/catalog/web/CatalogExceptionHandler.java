package com.createyourpizza.catalog.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.createyourpizza.catalog.service.CatalogBusyException;

@RestControllerAdvice
public class CatalogExceptionHandler {

	@ExceptionHandler(CatalogBusyException.class)
	public ResponseEntity<ApiEnvelope<Void>> handleBusy(CatalogBusyException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.header(HttpHeaders.RETRY_AFTER, "60")
				.body(ApiEnvelope.error(
						HttpStatus.SERVICE_UNAVAILABLE.value(),
						"please try after sometime",
						"system busy"));
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
