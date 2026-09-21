package com.createyourpizza.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthBadRequestException extends ResponseStatusException {

	public AuthBadRequestException(String reason) {
		super(HttpStatus.BAD_REQUEST, reason);
	}
}
