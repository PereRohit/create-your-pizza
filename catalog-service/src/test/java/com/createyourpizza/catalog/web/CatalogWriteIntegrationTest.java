package com.createyourpizza.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.createyourpizza.catalog.domain.CatalogMeta;
import com.createyourpizza.catalog.lock.CatalogLockKeys;
import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.repository.CatalogMetaRepository;
import com.createyourpizza.catalog.repository.ComboItemRepository;
import com.createyourpizza.catalog.repository.MenuPdfRepository;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.security.TestJwtSupport;
import com.createyourpizza.catalog.seed.CatalogMetaDefaults;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogWriteIntegrationTest {

	private static final String ISSUER = "create-your-pizza-auth";
	private static final String AUDIENCE = "create-your-pizza-catalog";

	private static final RSAKey RSA_KEY;
	private static final HttpServer JWKS_SERVER;
	private static final Queue<String> JWKS_RESPONSES = new ArrayDeque<>();
	private static final String JWKS_URI;

	static {
		try {
			RSA_KEY = new RSAKeyGenerator(2048)
					.keyUse(KeyUse.SIGNATURE)
					.algorithm(JWSAlgorithm.RS256)
					.keyID("kid-1")
					.generate();

			JWKS_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			JWKS_SERVER.createContext("/jwks", exchange -> {
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
	private CatalogLockStore lockStore;

	@Autowired
	private CatalogMetaRepository catalogMetaRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ComboItemRepository comboItemRepository;

	@Autowired
	private OptionEntityRepository optionEntityRepository;

	@Autowired
	private MenuPdfRepository menuPdfRepository;

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
	void setUp() {
		synchronized (JWKS_RESPONSES) {
			JWKS_RESPONSES.clear();
			JWKS_RESPONSES.add(TestJwtSupport.jwksJson(RSA_KEY));
		}
		lockStore.release(CatalogLockKeys.PDF_GENERATION);
		lockStore.release(CatalogLockKeys.CATALOG_WRITE);
		menuPdfRepository.deleteAll();
		comboItemRepository.deleteAll();
		optionEntityRepository.deleteAll();
		productRepository.deleteAll();
		catalogMetaRepository.deleteAll();

		CatalogMeta meta = CatalogMetaDefaults.newSeedRow(Instant.parse("2026-01-01T00:00:00Z"));
		meta.setDirty(false);
		meta.setLastPdfVersion(3);
		catalogMetaRepository.save(meta);
	}

	@Test
	void adminCanCrudProductsAndOptionsAndSetsDirtyWithoutBumpingPdfVersion() throws Exception {
		String admin = adminToken();

		MvcResult simpleResult = mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Garlic Bread",
								  "productType": "simple",
								  "productCategory": "veg",
								  "productPrice": 80.00,
								  "active": true
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value(201))
				.andExpect(jsonPath("$.data.productType").value("simple"))
				.andExpect(jsonPath("$.data.productName").value("Garlic Bread"))
				.andReturn();

		String simpleId = readJson(simpleResult, "$.data.productId");

		MvcResult wingsResult = mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Wings",
								  "productType": "simple",
								  "productCategory": "non-veg",
								  "productPrice": 150.00
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		String wingsId = readJson(wingsResult, "$.data.productId");

		mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Snack Combo",
								  "productType": "combo",
								  "productCategory": "non-veg",
								  "productPrice": 199.00,
								  "simpleIds": ["%s", "%s"]
								}
								""".formatted(simpleId, wingsId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.productType").value("combo"))
				.andExpect(jsonPath("$.data.productPrice").value(199.00))
				.andExpect(jsonPath("$.data.simpleIds.length()").value(2));

		MvcResult pizzaResult = mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Margherita",
								  "productType": "pizza",
								  "productCategory": "veg",
								  "productPrice": 299.00,
								  "optionsEnabled": true,
								  "customisationNotes": "Extra basil"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.productType").value("pizza-base"))
				.andExpect(jsonPath("$.data.optionsEnabled").value(true))
				.andReturn();
		String pizzaId = readJson(pizzaResult, "$.data.productId");

		mockMvc.perform(put("/api/products/" + pizzaId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Margherita",
								  "productType": "pizza",
								  "productCategory": "veg",
								  "productPrice": 320.00,
								  "optionsEnabled": false
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.productPrice").value(320.00))
				.andExpect(jsonPath("$.data.optionsEnabled").value(false));

		MvcResult optionResult = mockMvc.perform(post("/api/options")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "optionKind": "TOPPING",
								  "productName": "jalapeno",
								  "productPrice": 20.00,
								  "isBase": false
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.productType").value("pizza-spec"))
				.andExpect(jsonPath("$.data.optionKind").value("TOPPING"))
				.andReturn();
		String optionId = readJson(optionResult, "$.data.productId");

		mockMvc.perform(put("/api/options/" + optionId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "optionKind": "TOPPING",
								  "productName": "jalapeno",
								  "productPrice": 25.00,
								  "isBase": false
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.productPrice").value(25.00));

		mockMvc.perform(delete("/api/options/" + optionId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());

		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isEqualTo(3);
		assertThat(menuPdfRepository.count()).isZero();
		assertThat(lockStore.isHeld(CatalogLockKeys.CATALOG_WRITE)).isFalse();
	}

	@Test
	void pdfLockHeldReturns503WithRetryAfter() throws Exception {
		lockStore.tryAcquire(CatalogLockKeys.PDF_GENERATION, "pdf-job", Duration.ofSeconds(120));

		mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "X",
								  "productType": "simple",
								  "productCategory": "veg",
								  "productPrice": 10.00
								}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.message").value("please try after sometime"))
				.andExpect(jsonPath("$.error").value("system busy"))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.pagination").doesNotExist());

		assertThat(productRepository.count()).isZero();
		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		assertThat(meta.isDirty()).isFalse();
		assertThat(meta.getLastPdfVersion()).isEqualTo(3);
	}

	@Test
	void writeLockBusyReturns503() throws Exception {
		lockStore.tryAcquire(CatalogLockKeys.CATALOG_WRITE, "other-writer", Duration.ofSeconds(30));

		mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "X",
								  "productType": "simple",
								  "productCategory": "veg",
								  "productPrice": 10.00
								}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
				.andExpect(jsonPath("$.error").value("system busy"));
	}

	@Test
	void trustedJwtCannotWrite() throws Exception {
		String trusted = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read menu:read",
				List.of("TRUSTED_SYSTEM"),
				Instant.now().plusSeconds(600));

		mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + trusted)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "X",
								  "productType": "simple",
								  "productCategory": "veg",
								  "productPrice": 10.00
								}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void adminWriteScopeWithoutAdminRoleIsForbidden() throws Exception {
		String token = TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:write catalog:read",
				List.of("TRUSTED_SYSTEM"),
				Instant.now().plusSeconds(600));

		mockMvc.perform(post("/api/options")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "optionKind": "TOPPING",
								  "productName": "x",
								  "productPrice": 1.00,
								  "isBase": false
								}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void deleteProductReleasesMembershipAndSetsDirty() throws Exception {
		String admin = adminToken();

		MvcResult breadResult = mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Garlic Bread",
								  "productType": "simple",
								  "productCategory": "veg",
								  "productPrice": 80.00
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		UUID breadId = UUID.fromString(readJson(breadResult, "$.data.productId"));

		MvcResult wingsResult = mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Wings",
								  "productType": "simple",
								  "productCategory": "non-veg",
								  "productPrice": 150.00
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		UUID wingsId = UUID.fromString(readJson(wingsResult, "$.data.productId"));

		MvcResult comboResult = mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Snack Combo",
								  "productType": "combo",
								  "productCategory": "non-veg",
								  "productPrice": 199.00,
								  "simpleIds": ["%s", "%s"]
								}
								""".formatted(breadId, wingsId)))
				.andExpect(status().isCreated())
				.andReturn();
		UUID comboId = UUID.fromString(readJson(comboResult, "$.data.productId"));

		assertThat(comboItemRepository.findByComboId(comboId)).hasSize(2);

		mockMvc.perform(delete("/api/products/" + comboId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());

		assertThat(productRepository.findById(comboId)).isEmpty();
		assertThat(comboItemRepository.findByComboId(comboId)).isEmpty();
		assertThat(productRepository.findById(breadId)).isPresent();
		assertThat(productRepository.findById(wingsId)).isPresent();

		MvcResult combo2Result = mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Snack Combo 2",
								  "productType": "combo",
								  "productCategory": "non-veg",
								  "productPrice": 210.00,
								  "simpleIds": ["%s", "%s"]
								}
								""".formatted(breadId, wingsId)))
				.andExpect(status().isCreated())
				.andReturn();
		UUID combo2Id = UUID.fromString(readJson(combo2Result, "$.data.productId"));
		assertThat(comboItemRepository.findByComboId(combo2Id)).hasSize(2);

		mockMvc.perform(delete("/api/products/" + breadId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());

		assertThat(productRepository.findById(breadId)).isEmpty();
		assertThat(comboItemRepository.findByComboId(combo2Id))
				.extracting(item -> item.getSimpleId())
				.containsExactly(wingsId);
		assertThat(comboItemRepository.findAll())
				.noneMatch(item -> breadId.equals(item.getSimpleId()));

		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isEqualTo(3);
	}

	private String adminToken() throws Exception {
		return TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read catalog:write menu:read",
				List.of("ADMIN"),
				Instant.now().plusSeconds(600));
	}

	private static String readJson(MvcResult result, String path) throws Exception {
		String body = result.getResponse().getContentAsString();
		com.jayway.jsonpath.JsonPath jsonPath = com.jayway.jsonpath.JsonPath.compile(path);
		Object value = jsonPath.read(body);
		return String.valueOf(value);
	}
}
