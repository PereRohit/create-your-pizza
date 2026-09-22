package com.createyourpizza.catalog.security;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

final class TestJwtSupport {

	private TestJwtSupport() {
	}

	static String jwksJson(RSAKey publicKey) {
		return "{\"keys\":[" + publicKey.toPublicJWK().toJSONString() + "]}";
	}

	static String sign(
			RSAKey key,
			String kid,
			String issuer,
			String audience,
			String scope,
			Instant exp) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(UUID.randomUUID().toString())
				.issuer(issuer)
				.audience(audience)
				.issueTime(Date.from(Instant.now()))
				.expirationTime(Date.from(exp))
				.jwtID(UUID.randomUUID().toString())
				.claim("scope", scope)
				.claim("roles", List.of("ADMIN"))
				.build();

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(kid)
				.type(JOSEObjectType.JWT)
				.build();

		SignedJWT signed = new SignedJWT(header, claims);
		signed.sign(new RSASSASigner(key.toPrivateKey()));
		return signed.serialize();
	}
}
