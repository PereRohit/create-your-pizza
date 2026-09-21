package com.createyourpizza.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthForbiddenException extends ResponseStatusException {

	public AuthForbiddenException(String reason) {
		super(HttpStatus.FORBIDDEN, reason);
	}
}
