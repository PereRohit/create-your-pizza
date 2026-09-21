package com.createyourpizza.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.createyourpizza.auth.domain.VerificationKey;
import com.createyourpizza.auth.jwt.JwtIssuer;
import com.createyourpizza.auth.jwt.SigningKeyService;
import com.createyourpizza.auth.repository.VerificationKeyRepository;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

@SpringBootTest
@AutoConfigureMockMvc
class JwksAndJwtIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VerificationKeyRepository verificationKeyRepository;

	@Autowired
	private SigningKeyService signingKeyService;

	@Autowired
	private JwtIssuer jwtIssuer;

	@Test
	void jwksReturnsActivePublicKeysWithoutPrivateMaterial() throws Exception {
		MvcResult result = mockMvc.perform(get("/auth/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys").isArray())
				.andExpect(jsonPath("$.keys.length()").value(1))
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].kid").value(signingKeyService.kid()))
				.andExpect(jsonPath("$.keys[0].n").exists())
				.andExpect(jsonPath("$.keys[0].e").exists())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("\"d\"");
		assertThat(body).doesNotContain("\"p\"");
		assertThat(body).doesNotContain("\"q\"");

		VerificationKey stored = verificationKeyRepository.findById(signingKeyService.kid()).orElseThrow();
		Map<String, Object> publicJwk = stored.getPublicJwk();
		assertThat(publicJwk).doesNotContainKey("d");
		assertThat(stored.getAlg()).isEqualTo("RS256");
		assertThat(stored.isActive()).isTrue();
	}

	@Test
	void issuedTokenVerifiesAgainstJwksPublicKey() throws Exception {
		String token = jwtIssuer.issue(
				"user-1",
				List.of("ADMIN"),
				"catalog:read catalog:write menu:read",
				null);

		SignedJWT jwt = SignedJWT.parse(token);
		RSAKey publicKey = signingKeyService.getSigningKey().toPublicJWK();
		JWSVerifier verifier = new RSASSAVerifier(publicKey.toRSAPublicKey());
		assertThat(jwt.verify(verifier)).isTrue();
		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("create-your-pizza-auth");
		assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("create-your-pizza-catalog");
	}

	@Test
	void validateEndpointDoesNotExist() throws Exception {
		mockMvc.perform(post("/auth/validate"))
				.andExpect(status().isNotFound());
	}

	@Test
	void jwksJsonParsesAsStandardKeysDocument() throws Exception {
		MvcResult result = mockMvc.perform(get("/auth/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("\"keys\"");
		assertThat(body).contains("\"kty\"");
		assertThat(body).contains("\"kid\"");
	}
}
