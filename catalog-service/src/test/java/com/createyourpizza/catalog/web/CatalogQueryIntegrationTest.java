package com.createyourpizza.catalog.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
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
class CatalogQueryIntegrationTest {

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
	void unauthenticatedListIs401() throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.pagination").doesNotExist());
	}

	@Test
	void adminAndTrustedSeeTheSameFilteredList() throws Exception {
		String admin = adminToken();
		seedCatalog(admin);

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("type", "simple")
						.param("category", "veg"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].productName").value("Garlic Bread"))
				.andExpect(jsonPath("$.pagination.current").value(1))
				.andExpect(jsonPath("$.pagination.next").value(-1))
				.andExpect(jsonPath("$.pagination.total").value(1));

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + trustedToken())
						.param("type", "simple")
						.param("category", "veg"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].productName").value("Garlic Bread"))
				.andExpect(jsonPath("$.pagination.next").value(-1));
	}

	@Test
	void untypedListIncludesPizzaSpecAndTypedPizzaSpecOmitsCategory() throws Exception {
		String admin = adminToken();
		seedCatalog(admin);

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.productType == 'simple')]").isNotEmpty())
				.andExpect(jsonPath("$.data[?(@.productType == 'pizza-spec')]").isNotEmpty())
				.andExpect(jsonPath("$.pagination.total").value(5));

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("type", "pizza-spec")
						.param("category", "veg"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].productType").value("pizza-spec"))
				.andExpect(jsonPath("$.data[0].optionKind").exists())
				.andExpect(jsonPath("$.data[0].isBase").exists())
				.andExpect(jsonPath("$.data[0].productCategory").doesNotExist())
				.andExpect(jsonPath("$.pagination.next").value(-1));
	}

	@Test
	void categoryFilterExcludesPizzaSpecAndMaxPriceIsStrictlyLess() throws Exception {
		String admin = adminToken();
		seedCatalog(admin);

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("category", "non-veg")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.productType == 'pizza-spec')]").isEmpty())
				.andExpect(jsonPath("$.data[?(@.productName == 'Wings')]").isNotEmpty())
				.andExpect(jsonPath("$.pagination.total").value(1));

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("maxPrice", "15")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.productName == 'Garlic Bread')]").isEmpty())
				.andExpect(jsonPath("$.data[?(@.productName == 'thin crust')]").isNotEmpty())
				.andExpect(jsonPath("$.pagination.total").value(1));
	}

	@Test
	void pizzaBaseIncludesOptionsEnabledAndGetByIdOmitsPagination() throws Exception {
		String admin = adminToken();
		seedCatalog(admin);

		MvcResult list = mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("type", "pizza-base"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].productType").value("pizza-base"))
				.andExpect(jsonPath("$.data[0].optionsEnabled").value(true))
				.andExpect(jsonPath("$.data[0].customisationNotes").value("Extra basil"))
				.andReturn();

		String pizzaId = readJson(list, "$.data[0].productId");
		mockMvc.perform(get("/api/products/" + pizzaId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.productId").value(pizzaId))
				.andExpect(jsonPath("$.pagination").doesNotExist());

		MvcResult options = mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("type", "pizza-spec"))
				.andExpect(status().isOk())
				.andReturn();
		String optionId = readJson(options, "$.data[0].productId");
		mockMvc.perform(get("/api/options/" + optionId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.productType").value("pizza-spec"))
				.andExpect(jsonPath("$.pagination").doesNotExist());

		mockMvc.perform(get("/api/products/" + optionId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isNotFound());
	}

	@Test
	void inactiveProductsAreHiddenFromListAndSizeClampStill200() throws Exception {
		String admin = adminToken();
		mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Hidden",
								  "productType": "simple",
								  "productCategory": "veg",
								  "productPrice": 10.00,
								  "active": false
								}
								"""))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "Visible",
								  "productType": "simple",
								  "productCategory": "veg",
								  "productPrice": 11.00,
								  "active": true
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("size", "200"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].productName").value("Visible"))
				.andExpect(jsonPath("$.pagination.next").value(-1));
	}

	@Test
	void lastPageHasNextMinusOne() throws Exception {
		String admin = adminToken();
		seedCatalog(admin);

		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.param("size", "4")
						.param("page", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagination.current").value(2))
				.andExpect(jsonPath("$.pagination.next").value(-1))
				.andExpect(jsonPath("$.pagination.total").value(5))
				.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void unknownTypeIs400() throws Exception {
		mockMvc.perform(get("/api/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
						.param("type", "dessert"))
				.andExpect(status().isBadRequest());
	}

	private void seedCatalog(String admin) throws Exception {
		mockMvc.perform(post("/api/products")
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
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/products")
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
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/products")
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
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/options")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "thin crust",
								  "optionKind": "CRUST_TYPE",
								  "productPrice": 10.00,
								  "isBase": true
								}
								"""))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/options")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "productName": "deep dish",
								  "optionKind": "CRUST_TYPE",
								  "productPrice": 25.00,
								  "isBase": false
								}
								"""))
				.andExpect(status().isCreated());
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

	private String trustedToken() throws Exception {
		return TestJwtSupport.sign(
				RSA_KEY,
				"kid-1",
				ISSUER,
				AUDIENCE,
				"catalog:read menu:read",
				List.of("TRUSTED_SYSTEM"),
				Instant.now().plusSeconds(600));
	}

	private static String readJson(MvcResult result, String path) throws Exception {
		String body = result.getResponse().getContentAsString();
		com.jayway.jsonpath.JsonPath jsonPath = com.jayway.jsonpath.JsonPath.compile(path);
		Object value = jsonPath.read(body);
		return String.valueOf(value);
	}
}
