package com.createyourpizza.auth.jwt;

public final class AuthScopes {

	public static final String ADMIN = "catalog:read catalog:write menu:read";

	public static final String TRUSTED = "catalog:read menu:read";

	private AuthScopes() {
	}
}
