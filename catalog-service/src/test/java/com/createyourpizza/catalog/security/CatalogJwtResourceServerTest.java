package com.createyourpizza.catalog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.createyourpizza.catalog.menu.InMemoryLatestMenuStore;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.repository.MenuPdfRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogJwtResourceServerTest {

	private static final String ISSUER = "create-your-pizza-auth";
	private static final String AUDIENCE = "create-your-pizza-catalog";

	private static final RSAKey RSA_KEY;
	private static final RSAKey ROTATED_KEY;
	private static final HttpServer JWKS_SERVER;
	private static final Queue<String> JWKS_RESPONSES = new ArrayDeque<>();
	private static final AtomicInteger JWKS_HITS = new AtomicInteger();
	private static final String JWKS_URI;

	static {
		try {
			RSA_KEY = new RSAKeyGenerator(2048)
					.keyUse(KeyUse.SIGNATURE)
					.algorithm(JWSAlgorithm.RS256)
					.keyID("kid-1")
					.generate();
			ROTATED_KEY = new RSAKeyGenerator(2048)
					.keyUse(KeyUse.SIGNATURE)
					.algorithm(JWSAlgorithm.RS256)
					.keyID("kid-2")
					.generate();

			JWKS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			JWKS_SERVER.createContext("/jwks", exchange -> {
				JWKS_HITS.incrementAndGet();
				String body;
				synchronized (JWKS_RESPONSES) {
					body = JWKS_RESPONSES.poll();
				}
				if (body == null) {
					body = TestJwtSupport.jwksJson(RSA_KEY);
				}
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
				exchange.sendResponseHeaders(200, bytes.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(bytes);
				}
			});
			JWKS_SERVER.setExecutor(Executors.newCachedThreadPool());
			JWKS_SERVER.start();
			JWKS_URI = "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks";
		}
		catch (Exception ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MenuPdfRepository menuPdfRepository;

	@Autowired
	private LatestMenuStore latestMenuStore;

	@AfterAll
	static void stopJwks() {
		JWKS_SERVER.stop(0);
	}

	@DynamicPropertySource
	static void registerJwksUri(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> JWKS_URI);
		registry.add("app.jwt.jwks-warmup", () -> "false");
	}

	@BeforeEach
	void resetJwksQueue() {
		synchronized (JWKS_RESPONSES) {
			JWKS_RESPONSES.clear();
			JWKS_RESPONSES.add(TestJwtSupport.jwksJson(RSA_KEY));
		}
		JWKS_HITS.set(0);
	}

	@Test
	void missingJwtReturns401Envelope() throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"));
	}

	@Test
	void validTokenWithCatalogReadIsAccepted() throws Exception {
		String token = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read menu:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.pagination.next").value(-1));
	}

	@Test
	void badSignatureReturns401() throws Exception {
		RSAKey other = new RSAKeyGenerator(2048)
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyID("kid-1")
				.generate();
		String token = TestJwtSupport.sign(
				other,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void wrongIssuerReturns401() throws Exception {
		String token = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				"wrong-issuer",
				AUDIENCE,
				"catalog:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongAudienceReturns401() throws Exception {
		String token = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				"wrong-audience",
				"catalog:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongScopeReturns403() throws Exception {
		String token = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"menu:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"));
	}

	@Test
	void writeWithoutCatalogWriteScopeReturns403() throws Exception {
		String token = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read menu:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void publicMenuPdfDoesNotRequireJwt() throws Exception {
		menuPdfRepository.deleteAll();
		if (latestMenuStore instanceof InMemoryLatestMenuStore memory) {
			memory.clear();
		}
		mockMvc.perform(get("/api/menu.pdf")).andExpect(status().isNotFound());
	}

	@Test
	void expiredJwtReturns401() throws Exception {
		String token = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read",
				Instant.now().minusSeconds(60));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void unknownKidRefetchesJwksAndAcceptsToken() throws Exception {
		synchronized (JWKS_RESPONSES) {
			JWKS_RESPONSES.clear();
			JWKS_RESPONSES.add(TestJwtSupport.jwksJson(RSA_KEY));
		}
		JWKS_HITS.set(0);

		String knownKidToken = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + knownKidToken))
				.andExpect(status().isOk());

		int hitsAfterCacheWarm = JWKS_HITS.get();
		assertThat(hitsAfterCacheWarm).isGreaterThanOrEqualTo(0);

		// Known kid must be served from the in-memory cache (no additional HTTP GET).
		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + knownKidToken))
				.andExpect(status().isOk());
		assertThat(JWKS_HITS.get()).isEqualTo(hitsAfterCacheWarm);

		synchronized (JWKS_RESPONSES) {
			JWKS_RESPONSES.clear();
			JWKS_RESPONSES.add(TestJwtSupport.jwksJson(ROTATED_KEY));
		}

		String rotatedToken = TestJwtSupport.sign(
				ROTATED_KEY,
				"kid-2",
				ISSUER,
				AUDIENCE,
				"catalog:read",
				Instant.now().plusSeconds(600));

		mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + rotatedToken))
				.andExpect(status().isOk());

		assertThat(JWKS_HITS.get()).isEqualTo(hitsAfterCacheWarm + 1);
	}
}
