package com.createyourpizza.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;

class CachingRemoteJwkSourceTest {

	private HttpServer server;
	private String jwksUri;
	private final AtomicInteger hits = new AtomicInteger();
	private final AtomicReference<String> responseBody = new AtomicReference<>();

	@BeforeEach
	void startServer() throws Exception {
		hits.set(0);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/jwks", exchange -> {
			hits.incrementAndGet();
			byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		jwksUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
	}

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void loadSeedsInMemorySetFromHttp() throws Exception {
		RSAKey key = newKey("kid-1");
		responseBody.set(jwks(key));
		CachingRemoteJwkSource source = new CachingRemoteJwkSource(jwksUri, RestClient.create());

		source.load();

		assertThat(source.cachedSet().getKeyByKeyId("kid-1")).isNotNull();
		assertThat(hits.get()).isEqualTo(1);
	}

	@Test
	void loadRejectsResponseWithoutKeys() {
		responseBody.set("{\"oops\":true}");
		CachingRemoteJwkSource source = new CachingRemoteJwkSource(jwksUri, RestClient.create());

		assertThatThrownBy(source::load)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("did not contain keys");
	}

	@Test
	void unknownKidTriggersSecondHttpGet() throws Exception {
		RSAKey first = newKey("kid-1");
		RSAKey rotated = newKey("kid-2");
		responseBody.set(jwks(first));

		CachingRemoteJwkSource source = new CachingRemoteJwkSource(jwksUri, RestClient.create());
		source.load();
		assertThat(source.get(selector("kid-1"), null)).hasSize(1);
		assertThat(hits.get()).isEqualTo(1);

		responseBody.set(jwks(rotated));
		assertThat(source.get(selector("kid-2"), null)).hasSize(1);
		assertThat(hits.get()).isEqualTo(2);
		assertThat(source.cachedSet().getKeyByKeyId("kid-2")).isNotNull();
	}

	@Test
	void jwtPropertiesDefaultsMatchBuildPlan() {
		JwtProperties props = new JwtProperties();
		assertThat(props.getIssuer()).isEqualTo("create-your-pizza-auth");
		assertThat(props.getAudience()).isEqualTo("create-your-pizza-catalog");
		assertThat(props.isJwksWarmup()).isTrue();
	}

	private static JWKSelector selector(String kid) {
		return new JWKSelector(new JWKMatcher.Builder().keyID(kid).build());
	}

	private static RSAKey newKey(String kid) throws Exception {
		return new RSAKeyGenerator(2048)
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyID(kid)
				.generate();
	}

	private static String jwks(RSAKey key) {
		return "{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}";
	}
}
