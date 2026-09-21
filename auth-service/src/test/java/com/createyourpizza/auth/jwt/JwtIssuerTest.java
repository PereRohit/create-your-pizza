package com.createyourpizza.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.createyourpizza.auth.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jwt.SignedJWT;

@ExtendWith(MockitoExtension.class)
class JwtIssuerTest {

	@Mock
	private SigningKeyService signingKeyService;

	private JwtProperties jwtProperties;
	private JwtIssuer jwtIssuer;
	private RSAKey rsaKey;

	@BeforeEach
	void setUp() throws Exception {
		rsaKey = new RSAKeyGenerator(2048)
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyID("test-kid")
				.generate();

		when(signingKeyService.getSigningKey()).thenReturn(rsaKey);
		when(signingKeyService.kid()).thenReturn("test-kid");

		jwtProperties = new JwtProperties();
		jwtProperties.setIssuer("create-your-pizza-auth");
		jwtProperties.setAudience("create-your-pizza-catalog");
		jwtProperties.setTtl(Duration.ofMinutes(30));

		jwtIssuer = new JwtIssuer(signingKeyService, jwtProperties);
	}

	@Test
	void issuesRs256JwtWithLockedClaimsAndTtl() throws Exception {
		String subject = UUID.randomUUID().toString();
		Instant before = Instant.now().minusSeconds(1);

		String token = jwtIssuer.issue(
				subject,
				List.of("ADMIN"),
				"catalog:read catalog:write menu:read",
				null);

		Instant after = Instant.now().plusSeconds(1);

		SignedJWT jwt = SignedJWT.parse(token);
		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
		assertThat(jwt.getHeader().getKeyID()).isEqualTo("test-kid");
		assertThat(jwt.getHeader().getType().toString()).isEqualTo("JWT");

		JWSVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
		assertThat(jwt.verify(verifier)).isTrue();

		assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(subject);
		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("create-your-pizza-auth");
		assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("create-your-pizza-catalog");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("scope"))
				.isEqualTo("catalog:read catalog:write menu:read");
		assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ADMIN");
		assertThat(jwt.getJWTClaimsSet().getClaim("client_id")).isNull();
		assertThat(jwt.getJWTClaimsSet().getJWTID()).isNotBlank();

		Date iat = jwt.getJWTClaimsSet().getIssueTime();
		Date exp = jwt.getJWTClaimsSet().getExpirationTime();
		assertThat(iat.toInstant()).isBetween(before, after);
		assertThat(exp.toInstant()).isBetween(
				before.plus(Duration.ofMinutes(30)),
				after.plus(Duration.ofMinutes(30)));
	}

	@Test
	void includesClientIdForTrustedTokens() throws Exception {
		String token = jwtIssuer.issue(
				UUID.randomUUID().toString(),
				List.of("TRUSTED_SYSTEM"),
				"catalog:read menu:read",
				"client-abc");

		SignedJWT jwt = SignedJWT.parse(token);
		assertThat(jwt.getJWTClaimsSet().getStringClaim("client_id")).isEqualTo("client-abc");
		assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("TRUSTED_SYSTEM");
	}

	@Test
	void respectsConfiguredTtl() throws Exception {
		jwtProperties.setTtl(Duration.ofMinutes(5));
		Instant before = Instant.now().minusSeconds(1);

		String token = jwtIssuer.issue(
				UUID.randomUUID().toString(),
				List.of("ADMIN"),
				"catalog:read catalog:write menu:read",
				null);

		Instant after = Instant.now().plusSeconds(1);
		SignedJWT jwt = SignedJWT.parse(token);
		assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isBetween(
				before.plus(Duration.ofMinutes(5)),
				after.plus(Duration.ofMinutes(5)));
	}
}
