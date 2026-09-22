package com.createyourpizza.catalog.config;

import java.text.ParseException;
import java.util.List;

import org.springframework.web.client.RestClient;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * In-memory JWKS cache (Design §4.5–4.6 / Spec FR-19): load at startup (optional),
 * refetch only when no JWK matches the selector (unknown {@code kid}). No scheduled poll.
 */
public final class CachingRemoteJwkSource implements JWKSource<SecurityContext> {

	private final String jwkSetUri;
	private final RestClient restClient;
	private volatile JWKSet cachedSet = new JWKSet();

	public CachingRemoteJwkSource(String jwkSetUri, RestClient restClient) {
		this.jwkSetUri = jwkSetUri;
		this.restClient = restClient;
	}

	/** HTTP GET JWKS and replace the in-memory set. Fails fast at startup when warmup is enabled. */
	public void load() {
		cachedSet = fetchJwks();
	}

	JWKSet cachedSet() {
		return cachedSet;
	}

	@Override
	public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) throws KeySourceException {
		List<JWK> matches = jwkSelector.select(cachedSet);
		if (!matches.isEmpty()) {
			return matches;
		}
		synchronized (this) {
			matches = jwkSelector.select(cachedSet);
			if (!matches.isEmpty()) {
				return matches;
			}
			try {
				cachedSet = fetchJwks();
			}
			catch (IllegalStateException ex) {
				throw new KeySourceException(ex.getMessage(), ex);
			}
			return jwkSelector.select(cachedSet);
		}
	}

	private JWKSet fetchJwks() {
		String body = restClient.get()
				.uri(jwkSetUri)
				.retrieve()
				.body(String.class);
		if (body == null || !body.contains("\"keys\"")) {
			throw new IllegalStateException("JWKS response from " + jwkSetUri + " did not contain keys");
		}
		try {
			return JWKSet.parse(body);
		}
		catch (ParseException ex) {
			throw new IllegalStateException("Failed to parse JWKS from " + jwkSetUri, ex);
		}
	}
}
