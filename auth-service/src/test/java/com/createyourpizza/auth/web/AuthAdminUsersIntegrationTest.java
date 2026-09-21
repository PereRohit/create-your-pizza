package com.createyourpizza.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.createyourpizza.auth.domain.TrustedClientCredentials;
import com.createyourpizza.auth.domain.User;
import com.createyourpizza.auth.domain.UserRole;
import com.createyourpizza.auth.domain.UserStatus;
import com.createyourpizza.auth.repository.TrustedClientCredentialsRepository;
import com.createyourpizza.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAdminUsersIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TrustedClientCredentialsRepository credentialsRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private User admin;
	private String adminToken;

	@BeforeEach
	void setUp() throws Exception {
		credentialsRepository.deleteAll();
		userRepository.deleteAll();

		admin = new User();
		admin.setRole(UserRole.ADMIN);
		admin.setStatus(UserStatus.ACTIVE);
		admin.setUsername("admin-" + UUID.randomUUID().toString().substring(0, 8));
		admin.setPasswordHash(passwordEncoder.encode("admin-password"));
		admin = userRepository.save(admin);

		MvcResult login = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"admin-password"}
								""".formatted(admin.getUsername())))
				.andExpect(status().isOk())
				.andReturn();
		adminToken = objectMapper.readTree(login.getResponse().getContentAsString())
				.path("data")
				.path("accessToken")
				.asText();
	}

	@Test
	void createAdminRequiresJwtAndReturnsCreatedAdmin() throws Exception {
		mockMvc.perform(post("/auth/admins")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"chef","password":"chef-pass"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.pagination").doesNotExist());

		mockMvc.perform(post("/auth/admins")
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"chef","password":"chef-pass"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value(201))
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.username").value("chef"))
				.andExpect(jsonPath("$.data.userId").isNotEmpty())
				.andExpect(jsonPath("$.pagination").doesNotExist());
	}

	@Test
	void listUsersReturnsPaginationSiblingWithNextMinusOne() throws Exception {
		for (int i = 0; i < 2; i++) {
			User trusted = new User();
			trusted.setRole(UserRole.TRUSTED_SYSTEM);
			trusted.setStatus(UserStatus.PENDING);
			trusted.setDisplayName("Partner-" + i);
			userRepository.save(trusted);
		}

		mockMvc.perform(get("/auth/users")
						.header("Authorization", "Bearer " + adminToken)
						.param("page", "1")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.pagination.current").value(1))
				.andExpect(jsonPath("$.pagination.next").value(-1))
				.andExpect(jsonPath("$.pagination.total").value(3));
	}

	@Test
	void listUsersClampsPageSizeAndFilters() throws Exception {
		User pending = new User();
		pending.setRole(UserRole.TRUSTED_SYSTEM);
		pending.setStatus(UserStatus.PENDING);
		pending.setDisplayName("Pending Partner");
		userRepository.save(pending);

		mockMvc.perform(get("/auth/users")
						.header("Authorization", "Bearer " + adminToken)
						.param("role", "TRUSTED_SYSTEM")
						.param("status", "PENDING")
						.param("size", "1000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].role").value("TRUSTED_SYSTEM"))
				.andExpect(jsonPath("$.data[0].status").value("PENDING"))
				.andExpect(jsonPath("$.pagination.total").value(1));
	}

	@Test
	void approveReturnsSecretOnceAndEnablesToken() throws Exception {
		MvcResult register = mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"Partner POS"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		String userId = objectMapper.readTree(register.getResponse().getContentAsString())
				.path("data")
				.path("userId")
				.asText();

		MvcResult approve = mockMvc.perform(post("/auth/users/{id}/approve", userId)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.apiKey").isNotEmpty())
				.andExpect(jsonPath("$.data.apiSecret").isNotEmpty())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.pagination").doesNotExist())
				.andReturn();

		JsonNode approveData = objectMapper.readTree(approve.getResponse().getContentAsString()).path("data");
		String apiKey = approveData.path("apiKey").asText();
		String apiSecret = approveData.path("apiSecret").asText();

		mockMvc.perform(post("/auth/users/{id}/approve", userId)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isBadRequest());

		MvcResult tokenResult = mockMvc.perform(post("/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"apiKey":"%s","apiSecret":"%s"}
								""".formatted(apiKey, apiSecret)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andReturn();

		String trustedToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
				.path("data")
				.path("accessToken")
				.asText();
		SignedJWT jwt = SignedJWT.parse(trustedToken);
		assertThat(jwt.getJWTClaimsSet().getStringClaim("client_id")).isEqualTo(apiKey);
		assertThat(trustedToken).doesNotContain(apiSecret);

		mockMvc.perform(get("/auth/users")
						.header("Authorization", "Bearer " + trustedToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void denyPendingTrustedWithoutCredentials() throws Exception {
		MvcResult register = mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"Denied Partner"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		String userId = objectMapper.readTree(register.getResponse().getContentAsString())
				.path("data")
				.path("userId")
				.asText();

		mockMvc.perform(post("/auth/users/{id}/deny", userId)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DENIED"))
				.andExpect(jsonPath("$.data.apiKey").doesNotExist())
				.andExpect(jsonPath("$.pagination").doesNotExist());

		TrustedClientCredentials credentials = credentialsRepository.findByUser_Id(UUID.fromString(userId))
				.orElseThrow();
		assertThat(credentials.getApiKey()).isNull();
		assertThat(credentials.getSecretHash()).isNull();
	}

	@Test
	void revokeBlocksFurtherTokenExchange() throws Exception {
		User trusted = new User();
		trusted.setRole(UserRole.TRUSTED_SYSTEM);
		trusted.setStatus(UserStatus.ACTIVE);
		trusted.setDisplayName("Active Partner");
		trusted = userRepository.save(trusted);

		String apiKey = "api-" + UUID.randomUUID();
		String apiSecret = "plain-secret";
		TrustedClientCredentials credentials = new TrustedClientCredentials();
		credentials.setUser(trusted);
		credentials.setApiKey(apiKey);
		credentials.setSecretHash(passwordEncoder.encode(apiSecret));
		credentialsRepository.save(credentials);

		mockMvc.perform(post("/auth/users/{id}/revoke", trusted.getId())
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("REVOKED"));

		mockMvc.perform(post("/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"apiKey":"%s","apiSecret":"%s"}
								""".formatted(apiKey, apiSecret)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void cannotDeleteSelfButCanDeleteOther() throws Exception {
		User other = new User();
		other.setRole(UserRole.ADMIN);
		other.setStatus(UserStatus.ACTIVE);
		other.setUsername("other-admin");
		other.setPasswordHash(passwordEncoder.encode("x"));
		other = userRepository.save(other);

		mockMvc.perform(delete("/auth/users/{id}", admin.getId())
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.pagination").doesNotExist());

		mockMvc.perform(delete("/auth/users/{id}", other.getId())
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(200));

		assertThat(userRepository.findById(other.getId())).isEmpty();
		assertThat(userRepository.findById(admin.getId())).isPresent();
	}
}
