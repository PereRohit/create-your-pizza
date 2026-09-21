package com.createyourpizza.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthNotFoundException extends ResponseStatusException {

	public AuthNotFoundException(String reason) {
		super(HttpStatus.NOT_FOUND, reason);
	}
}
