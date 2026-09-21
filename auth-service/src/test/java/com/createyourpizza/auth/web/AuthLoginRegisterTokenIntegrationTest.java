package com.createyourpizza.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.nimbusds.jwt.SignedJWT;

@SpringBootTest
@AutoConfigureMockMvc
class AuthLoginRegisterTokenIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TrustedClientCredentialsRepository credentialsRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String adminUsername;
	private String adminPassword;

	@BeforeEach
	void setUpAdmin() {
		credentialsRepository.deleteAll();
		userRepository.deleteAll();

		adminUsername = "admin-" + UUID.randomUUID().toString().substring(0, 8);
		adminPassword = "test-admin-password";

		User admin = new User();
		admin.setRole(UserRole.ADMIN);
		admin.setStatus(UserStatus.ACTIVE);
		admin.setUsername(adminUsername);
		admin.setPasswordHash(passwordEncoder.encode(adminPassword));
		userRepository.save(admin);
	}

	@Test
	void loginSuccessReturnsAdminJwtWithScopesAndRoles() throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"%s"}
								""".formatted(adminUsername, adminPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.message").value("success"))
				.andExpect(jsonPath("$.error").value(""))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.expiresIn").value(1800))
				.andReturn();

		String token = extractAccessToken(result);
		SignedJWT jwt = SignedJWT.parse(token);
		assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ADMIN");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("scope"))
				.isEqualTo("catalog:read catalog:write menu:read");
		assertThat(jwt.getJWTClaimsSet().getClaim("client_id")).isNull();
		assertThat(token).doesNotContain(adminPassword);
	}

	@Test
	void loginFailsForWrongPassword() throws Exception {
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"wrong"}
								""".formatted(adminUsername)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void loginFailsForNonAdmin() throws Exception {
		User trusted = new User();
		trusted.setRole(UserRole.TRUSTED_SYSTEM);
		trusted.setStatus(UserStatus.ACTIVE);
		trusted.setUsername("trusted-login");
		trusted.setPasswordHash(passwordEncoder.encode("secret"));
		userRepository.save(trusted);

		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"trusted-login","password":"secret"}
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void registerCreatesPendingTrustedSystemAndIgnoresRole() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"Partner POS","role":"ADMIN"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value(201))
				.andExpect(jsonPath("$.message").value("success"))
				.andExpect(jsonPath("$.error").value(""))
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.role").value("TRUSTED_SYSTEM"))
				.andExpect(jsonPath("$.data.userId").isNotEmpty())
				.andExpect(jsonPath("$.data.apiSecret").doesNotExist())
				.andExpect(jsonPath("$.data.apiKey").doesNotExist());

		User registered = userRepository.findAll().stream()
				.filter(u -> u.getRole() == UserRole.TRUSTED_SYSTEM)
				.findFirst()
				.orElseThrow();
		assertThat(registered.getStatus()).isEqualTo(UserStatus.PENDING);
		assertThat(registered.getDisplayName()).isEqualTo("Partner POS");

		TrustedClientCredentials credentials = credentialsRepository.findAll().stream()
				.filter(c -> c.getUser().getId().equals(registered.getId()))
				.findFirst()
				.orElseThrow();
		assertThat(credentials.getApiKey()).isNull();
		assertThat(credentials.getSecretHash()).isNull();
	}

	@Test
	void tokenFailsUntilActive() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"Pending Partner"}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"apiKey":"no-such-key","apiSecret":"anything"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void tokenSucceedsForActiveUnrevokedTrustedSystemWithoutSecretInJwt() throws Exception {
		User trusted = new User();
		trusted.setRole(UserRole.TRUSTED_SYSTEM);
		trusted.setStatus(UserStatus.ACTIVE);
		trusted.setDisplayName("Active Partner");
		userRepository.save(trusted);

		String apiKey = "api-" + UUID.randomUUID();
		String apiSecret = "plain-secret-value";
		TrustedClientCredentials credentials = new TrustedClientCredentials();
		credentials.setUser(trusted);
		credentials.setApiKey(apiKey);
		credentials.setSecretHash(passwordEncoder.encode(apiSecret));
		credentialsRepository.save(credentials);

		MvcResult result = mockMvc.perform(post("/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"apiKey":"%s","apiSecret":"%s"}
								""".formatted(apiKey, apiSecret)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(200))
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andReturn();

		String token = extractAccessToken(result);
		SignedJWT jwt = SignedJWT.parse(token);
		assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("TRUSTED_SYSTEM");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("scope")).isEqualTo("catalog:read menu:read");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("client_id")).isEqualTo(apiKey);
		assertThat(token).doesNotContain(apiSecret);
		assertThat(jwt.getJWTClaimsSet().toString()).doesNotContain(apiSecret);
	}

	@Test
	void tokenFailsForPendingUserEvenWithSeededKey() throws Exception {
		User trusted = new User();
		trusted.setRole(UserRole.TRUSTED_SYSTEM);
		trusted.setStatus(UserStatus.PENDING);
		trusted.setDisplayName("Still Pending");
		userRepository.save(trusted);

		String apiKey = "pending-key";
		TrustedClientCredentials credentials = new TrustedClientCredentials();
		credentials.setUser(trusted);
		credentials.setApiKey(apiKey);
		credentials.setSecretHash(passwordEncoder.encode("secret"));
		credentialsRepository.save(credentials);

		mockMvc.perform(post("/auth/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"apiKey":"pending-key","apiSecret":"secret"}
								"""))
				.andExpect(status().isUnauthorized());
	}

	private static String extractAccessToken(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
		int end = body.indexOf('"', start);
		return body.substring(start, end);
	}
}
