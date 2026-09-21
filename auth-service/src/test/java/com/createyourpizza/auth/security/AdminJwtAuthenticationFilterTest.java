package com.createyourpizza.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.createyourpizza.auth.config.JwtProperties;
import com.createyourpizza.auth.jwt.AuthScopes;
import com.createyourpizza.auth.jwt.JwtIssuer;
import com.createyourpizza.auth.jwt.SigningKeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class AdminJwtAuthenticationFilterTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Mock
	private SigningKeyService signingKeyService;

	@Mock
	private FilterChain filterChain;

	private JwtProperties jwtProperties;
	private JwtIssuer jwtIssuer;
	private RSAKey rsaKey;
	private AdminJwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() throws Exception {
		SecurityContextHolder.clearContext();
		rsaKey = new RSAKeyGenerator(2048)
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyID("test-kid")
				.generate();

		lenient().when(signingKeyService.getSigningKey()).thenReturn(rsaKey);
		lenient().when(signingKeyService.kid()).thenReturn("test-kid");

		jwtProperties = new JwtProperties();
		jwtProperties.setIssuer("create-your-pizza-auth");
		jwtProperties.setAudience("create-your-pizza-catalog");
		jwtProperties.setTtl(Duration.ofMinutes(30));

		jwtIssuer = new JwtIssuer(signingKeyService, jwtProperties);
		filter = new AdminJwtAuthenticationFilter(signingKeyService, jwtProperties);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void missingAuthorizationHeaderReturns401Envelope() throws Exception {
		MockHttpServletRequest request = adminUsersRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Missing or invalid Authorization header");
		verify(filterChain, never()).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void invalidBearerTokenReturns401Envelope() throws Exception {
		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void validAdminJwtSetsSecurityContextAndContinues() throws Exception {
		String subject = UUID.randomUUID().toString();
		String token = jwtIssuer.issue(subject, List.of("ADMIN"), AuthScopes.ADMIN, null);

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(subject);
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_ADMIN");
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void validTrustedSystemJwtSetsSecurityContextAndContinues() throws Exception {
		String subject = UUID.randomUUID().toString();
		String token = jwtIssuer.issue(
				subject,
				List.of("TRUSTED_SYSTEM"),
				AuthScopes.TRUSTED,
				"client-key");

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_TRUSTED_SYSTEM");
	}

	@Test
	void emptyBearerTokenReturns401Envelope() throws Exception {
		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Missing or invalid Authorization header");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void wrongIssuerReturns401Envelope() throws Exception {
		String token = signJwt(validClaimsBuilder().issuer("wrong-issuer").build());

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void wrongAudienceReturns401Envelope() throws Exception {
		String token = signJwt(validClaimsBuilder().audience("wrong-audience").build());

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void missingAudienceReturns401Envelope() throws Exception {
		String token = signJwt(new JWTClaimsSet.Builder()
				.subject(UUID.randomUUID().toString())
				.issuer(jwtProperties.getIssuer())
				.expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000))
				.claim("roles", List.of("ADMIN"))
				.build());

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void expiredTokenReturns401Envelope() throws Exception {
		String token = signJwt(validClaimsBuilder()
				.expirationTime(new java.util.Date(System.currentTimeMillis() - 60_000))
				.build());

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void missingExpirationReturns401Envelope() throws Exception {
		String token = signJwt(new JWTClaimsSet.Builder()
				.subject(UUID.randomUUID().toString())
				.issuer(jwtProperties.getIssuer())
				.audience(jwtProperties.getAudience())
				.claim("roles", List.of("ADMIN"))
				.build());

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void blankSubjectReturns401Envelope() throws Exception {
		String token = signJwt(validClaimsBuilder().subject("   ").build());

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void missingSubjectReturns401Envelope() throws Exception {
		String token = signJwt(new JWTClaimsSet.Builder()
				.issuer(jwtProperties.getIssuer())
				.audience(jwtProperties.getAudience())
				.expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000))
				.claim("roles", List.of("ADMIN"))
				.build());

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void wrongSigningKeyReturns401Envelope() throws Exception {
		RSAKey otherKey = new RSAKeyGenerator(2048)
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyID("other-kid")
				.generate();

		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(UUID.randomUUID().toString())
				.issuer(jwtProperties.getIssuer())
				.audience(jwtProperties.getAudience())
				.expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000))
				.claim("roles", List.of("ADMIN"))
				.build();

		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("other-kid").build(),
				claims);
		jwt.sign(new RSASSASigner(otherKey.toRSAPrivateKey()));

		MockHttpServletRequest request = adminUsersRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.serialize());
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertUnauthorizedEnvelope(response, "Invalid token");
		verify(filterChain, never()).doFilter(request, response);
	}

	private JWTClaimsSet.Builder validClaimsBuilder() {
		return new JWTClaimsSet.Builder()
				.subject(UUID.randomUUID().toString())
				.issuer(jwtProperties.getIssuer())
				.audience(jwtProperties.getAudience())
				.expirationTime(new java.util.Date(System.currentTimeMillis() + 60_000))
				.claim("roles", List.of("ADMIN"));
	}

	private String signJwt(JWTClaimsSet claims) throws Exception {
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(),
				claims);
		jwt.sign(new RSASSASigner(rsaKey.toRSAPrivateKey()));
		return jwt.serialize();
	}

	private static MockHttpServletRequest adminUsersRequest() {
		return new MockHttpServletRequest("GET", "/auth/users");
	}

	private static void assertUnauthorizedEnvelope(MockHttpServletResponse response, String error)
			throws Exception {
		assertThat(response.getStatus()).isEqualTo(401);
		JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
		assertThat(body.get("status").asInt()).isEqualTo(401);
		assertThat(body.get("error").asText()).isEqualTo(error);
	}
}
