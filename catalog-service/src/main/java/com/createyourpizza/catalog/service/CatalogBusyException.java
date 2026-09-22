package com.createyourpizza.catalog.service;

/**
 * Catalog write blocked by PDF-generation or write lock (Design §6 → HTTP 503).
 */
public class CatalogBusyException extends RuntimeException {

	public CatalogBusyException() {
		super("system busy");
	}
}
