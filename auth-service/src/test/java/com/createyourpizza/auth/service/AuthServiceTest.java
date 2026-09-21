package com.createyourpizza.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.createyourpizza.auth.config.JwtProperties;
import com.createyourpizza.auth.domain.TrustedClientCredentials;
import com.createyourpizza.auth.domain.User;
import com.createyourpizza.auth.domain.UserRole;
import com.createyourpizza.auth.domain.UserStatus;
import com.createyourpizza.auth.jwt.AuthScopes;
import com.createyourpizza.auth.jwt.JwtIssuer;
import com.createyourpizza.auth.repository.TrustedClientCredentialsRepository;
import com.createyourpizza.auth.repository.UserRepository;
import com.createyourpizza.auth.web.dto.AccessTokenData;
import com.createyourpizza.auth.web.dto.RegisterData;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private TrustedClientCredentialsRepository credentialsRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtIssuer jwtIssuer;

	@Mock
	private JwtProperties jwtProperties;

	@InjectMocks
	private AuthService authService;

	@BeforeEach
	void setUp() {
		lenient().when(jwtProperties.getTtl()).thenReturn(Duration.ofMinutes(30));
	}

	@Test
	void loginIssuesAdminJwtWhenActiveAdmin() {
		User admin = adminUser("chef", "hash");
		when(userRepository.findByUsername("chef")).thenReturn(Optional.of(admin));
		when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
		when(jwtIssuer.issue(eq(admin.getId().toString()), eq(List.of("ADMIN")), eq(AuthScopes.ADMIN), isNull()))
				.thenReturn("admin-jwt");

		AccessTokenData data = authService.login("chef", "secret");

		assertThat(data.accessToken()).isEqualTo("admin-jwt");
		assertThat(data.tokenType()).isEqualTo("Bearer");
		assertThat(data.expiresIn()).isEqualTo(1800);
	}

	@Test
	void loginRejectsUnknownUser() {
		when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login("missing", "x"))
				.isInstanceOf(AuthFailureException.class);
		verify(jwtIssuer, never()).issue(any(), any(), any(), any());
	}

	@Test
	void loginRejectsNonAdmin() {
		User trusted = trustedUser(UserStatus.ACTIVE);
		trusted.setUsername("partner");
		trusted.setPasswordHash("hash");
		when(userRepository.findByUsername("partner")).thenReturn(Optional.of(trusted));

		assertThatThrownBy(() -> authService.login("partner", "secret"))
				.isInstanceOf(AuthFailureException.class);
		verify(jwtIssuer, never()).issue(any(), any(), any(), any());
	}

	@Test
	void loginRejectsInactiveAdmin() {
		User admin = adminUser("chef", "hash");
		admin.setStatus(UserStatus.REVOKED);
		when(userRepository.findByUsername("chef")).thenReturn(Optional.of(admin));

		assertThatThrownBy(() -> authService.login("chef", "secret"))
				.isInstanceOf(AuthFailureException.class);
	}

	@Test
	void loginRejectsWrongPassword() {
		User admin = adminUser("chef", "hash");
		when(userRepository.findByUsername("chef")).thenReturn(Optional.of(admin));
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThatThrownBy(() -> authService.login("chef", "wrong"))
				.isInstanceOf(AuthFailureException.class);
	}

	@Test
	void registerCreatesPendingTrustedSystemWithoutSecret() {
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});
		when(credentialsRepository.save(any(TrustedClientCredentials.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		RegisterData data = authService.register("Partner POS");

		assertThat(data.status()).isEqualTo(UserStatus.PENDING);
		assertThat(data.role()).isEqualTo(UserRole.TRUSTED_SYSTEM);
		assertThat(data.userId()).isNotNull();

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User saved = userCaptor.getValue();
		assertThat(saved.getDisplayName()).isEqualTo("Partner POS");
		assertThat(saved.getRole()).isEqualTo(UserRole.TRUSTED_SYSTEM);
		assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
		assertThat(saved.getPasswordHash()).isNull();

		ArgumentCaptor<TrustedClientCredentials> credCaptor =
				ArgumentCaptor.forClass(TrustedClientCredentials.class);
		verify(credentialsRepository).save(credCaptor.capture());
		assertThat(credCaptor.getValue().getApiKey()).isNull();
		assertThat(credCaptor.getValue().getSecretHash()).isNull();
	}

	@Test
	void exchangeTokenFailsForPendingCredentials() {
		when(credentialsRepository.findByApiKey("key-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.exchangeToken("key-1", "secret"))
				.isInstanceOf(AuthFailureException.class);
		verify(jwtIssuer, never()).issue(any(), any(), any(), any());
	}

	@Test
	void exchangeTokenFailsWhenUserStillPendingEvenIfKeyExists() {
		User user = trustedUser(UserStatus.PENDING);
		TrustedClientCredentials credentials = credentials(user, "key-1", "hash", null);
		when(credentialsRepository.findByApiKey("key-1")).thenReturn(Optional.of(credentials));

		assertThatThrownBy(() -> authService.exchangeToken("key-1", "secret"))
				.isInstanceOf(AuthFailureException.class);
		verify(jwtIssuer, never()).issue(any(), any(), any(), any());
	}

	@Test
	void exchangeTokenFailsWhenRevoked() {
		User user = trustedUser(UserStatus.ACTIVE);
		TrustedClientCredentials credentials = credentials(user, "key-1", "hash", Instant.now());
		when(credentialsRepository.findByApiKey("key-1")).thenReturn(Optional.of(credentials));

		assertThatThrownBy(() -> authService.exchangeToken("key-1", "secret"))
				.isInstanceOf(AuthFailureException.class);
	}

	@Test
	void exchangeTokenIssuesTrustedJwtWhenActiveAndUnrevoked() {
		User user = trustedUser(UserStatus.ACTIVE);
		TrustedClientCredentials credentials = credentials(user, "key-1", "hash", null);
		when(credentialsRepository.findByApiKey("key-1")).thenReturn(Optional.of(credentials));
		when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
		when(jwtIssuer.issue(
				eq(user.getId().toString()),
				eq(List.of("TRUSTED_SYSTEM")),
				eq(AuthScopes.TRUSTED),
				eq("key-1"))).thenReturn("trusted-jwt");

		AccessTokenData data = authService.exchangeToken("key-1", "secret");

		assertThat(data.accessToken()).isEqualTo("trusted-jwt");
		assertThat(data.expiresIn()).isEqualTo(1800);
	}

	@Test
	void exchangeTokenRejectsWrongSecret() {
		User user = trustedUser(UserStatus.ACTIVE);
		TrustedClientCredentials credentials = credentials(user, "key-1", "hash", null);
		when(credentialsRepository.findByApiKey("key-1")).thenReturn(Optional.of(credentials));
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThatThrownBy(() -> authService.exchangeToken("key-1", "wrong"))
				.isInstanceOf(AuthFailureException.class);
	}

	private static User adminUser(String username, String passwordHash) {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setRole(UserRole.ADMIN);
		user.setStatus(UserStatus.ACTIVE);
		user.setUsername(username);
		user.setPasswordHash(passwordHash);
		return user;
	}

	private static User trustedUser(UserStatus status) {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setRole(UserRole.TRUSTED_SYSTEM);
		user.setStatus(status);
		user.setDisplayName("Partner");
		return user;
	}

	private static TrustedClientCredentials credentials(
			User user, String apiKey, String secretHash, Instant revokedAt) {
		TrustedClientCredentials credentials = new TrustedClientCredentials();
		credentials.setId(UUID.randomUUID());
		credentials.setUser(user);
		credentials.setApiKey(apiKey);
		credentials.setSecretHash(secretHash);
		credentials.setRevokedAt(revokedAt);
		return credentials;
	}
}
