package com.createyourpizza.auth.jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.createyourpizza.auth.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtIssuer {

	private final SigningKeyService signingKeyService;
	private final JwtProperties jwtProperties;

	public String issue(String subject, List<String> roles, String scope, String clientId) {
		try {
			Instant now = Instant.now();
			Instant exp = now.plus(jwtProperties.getTtl());

			JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
					.subject(subject)
					.issuer(jwtProperties.getIssuer())
					.audience(jwtProperties.getAudience())
					.issueTime(Date.from(now))
					.expirationTime(Date.from(exp))
					.jwtID(UUID.randomUUID().toString())
					.claim("scope", scope)
					.claim("roles", roles);

			if (clientId != null && !clientId.isBlank()) {
				claims.claim("client_id", clientId);
			}

			JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
					.keyID(signingKeyService.kid())
					.type(JOSEObjectType.JWT)
					.build();

			SignedJWT signed = new SignedJWT(header, claims.build());
			signed.sign(new RSASSASigner(signingKeyService.getSigningKey().toPrivateKey()));
			return signed.serialize();
		}
		catch (JOSEException e) {
			throw new IllegalStateException("Failed to sign JWT", e);
		}
	}
}
