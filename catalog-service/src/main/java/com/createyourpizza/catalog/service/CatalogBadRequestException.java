package com.createyourpizza.catalog.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CatalogBadRequestException extends ResponseStatusException {

	public CatalogBadRequestException(String reason) {
		super(HttpStatus.BAD_REQUEST, reason);
	}
}
