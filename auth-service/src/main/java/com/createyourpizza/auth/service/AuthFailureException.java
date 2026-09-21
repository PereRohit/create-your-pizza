package com.createyourpizza.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthFailureException extends ResponseStatusException {

	public AuthFailureException(String reason) {
		super(HttpStatus.UNAUTHORIZED, reason);
	}
}
