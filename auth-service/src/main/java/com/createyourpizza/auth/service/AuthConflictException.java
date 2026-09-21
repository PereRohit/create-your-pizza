package com.createyourpizza.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthConflictException extends ResponseStatusException {

	public AuthConflictException(String reason) {
		super(HttpStatus.CONFLICT, reason);
	}
}
