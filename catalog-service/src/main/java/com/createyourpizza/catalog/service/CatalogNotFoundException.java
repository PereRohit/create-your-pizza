package com.createyourpizza.catalog.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CatalogNotFoundException extends ResponseStatusException {

	public CatalogNotFoundException(String reason) {
		super(HttpStatus.NOT_FOUND, reason);
	}
}
