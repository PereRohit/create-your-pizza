package com.createyourpizza.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

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

	@Test
	void createAdminPersistsActiveAdmin() {
		when(userRepository.existsByUsername("chef")).thenReturn(false);
		when(passwordEncoder.encode("secret")).thenReturn("encoded");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});

		var data = authService.createAdmin("chef", "secret");

		assertThat(data.username()).isEqualTo("chef");
		assertThat(data.role()).isEqualTo(UserRole.ADMIN);
		assertThat(data.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(data.userId()).isNotNull();

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
	}

	@Test
	void createAdminRejectsDuplicateUsername() {
		when(userRepository.existsByUsername("chef")).thenReturn(true);

		assertThatThrownBy(() -> authService.createAdmin("chef", "secret"))
				.isInstanceOf(AuthConflictException.class);
		verify(userRepository, never()).save(any());
	}

	@Test
	void approveIssuesCredentialsOnceForPendingTrusted() {
		User user = trustedUser(UserStatus.PENDING);
		TrustedClientCredentials credentials = credentials(user, null, null, null);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(credentialsRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(credentials));
		when(passwordEncoder.encode(any())).thenReturn("secret-hash");
		when(credentialsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var data = authService.approve(user.getId());

		assertThat(data.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(data.apiKey()).isNotBlank();
		assertThat(data.apiSecret()).isNotBlank();
		assertThat(credentials.getApiKey()).isEqualTo(data.apiKey());
		assertThat(credentials.getSecretHash()).isEqualTo("secret-hash");
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void approveRejectsNonPending() {
		User user = trustedUser(UserStatus.ACTIVE);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> authService.approve(user.getId()))
				.isInstanceOf(AuthBadRequestException.class);
	}

	@Test
	void denySetsDeniedForPendingTrusted() {
		User user = trustedUser(UserStatus.PENDING);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var data = authService.deny(user.getId());

		assertThat(data.status()).isEqualTo(UserStatus.DENIED);
		assertThat(user.getStatus()).isEqualTo(UserStatus.DENIED);
	}

	@Test
	void revokeSetsRevokedAndTimestamp() {
		User user = trustedUser(UserStatus.ACTIVE);
		TrustedClientCredentials credentials = credentials(user, "key-1", "hash", null);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(credentialsRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(credentials));
		when(credentialsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var data = authService.revoke(user.getId());

		assertThat(data.status()).isEqualTo(UserStatus.REVOKED);
		assertThat(credentials.getRevokedAt()).isNotNull();
	}

	@Test
	void deleteUserRejectsSelf() {
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> authService.deleteUser(id, id.toString()))
				.isInstanceOf(AuthForbiddenException.class);
		verify(userRepository, never()).delete(any(User.class));
	}

	@Test
	void listUsersDefaultsPageSizeAndNextWhenNoMorePages() {
		Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
		when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), any(Pageable.class))).thenReturn(page);

		AuthService.UserListPage result = authService.listUsers(null, null, null, null);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
		assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
		assertThat(result.pagination().current()).isEqualTo(1);
		assertThat(result.pagination().next()).isEqualTo(-1);
		assertThat(result.pagination().total()).isZero();
	}

	@Test
	void listUsersClampsPageSizeToMax100() {
		Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
		when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), any(Pageable.class))).thenReturn(page);

		authService.listUsers(null, null, 1, 500);

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(userRepository).findAll(ArgumentMatchers.<Specification<User>>any(), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	void listUsersSetsNextPageWhenMoreResultsExist() {
		User user = trustedUser(UserStatus.ACTIVE);
		Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 25);
		when(userRepository.findAll(ArgumentMatchers.<Specification<User>>any(), any(Pageable.class))).thenReturn(page);

		AuthService.UserListPage result = authService.listUsers(null, null, 1, 10);

		assertThat(result.pagination().next()).isEqualTo(2);
		assertThat(result.pagination().total()).isEqualTo(25);
		assertThat(result.items()).singleElement()
				.satisfies(item -> {
					assertThat(item.userId()).isEqualTo(user.getId());
					assertThat(item.displayName()).isEqualTo("Partner");
				});
	}

	@Test
	@SuppressWarnings("unchecked")
	void listUsersAppliesRoleAndStatusFilters() {
		Page<User> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
		when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

		authService.listUsers(UserRole.ADMIN, UserStatus.ACTIVE, 1, 10);

		ArgumentCaptor<Specification<User>> specCaptor = ArgumentCaptor.forClass(Specification.class);
		verify(userRepository).findAll(specCaptor.capture(), any(Pageable.class));

		Root<User> root = mock(Root.class);
		CriteriaQuery<?> query = mock(CriteriaQuery.class);
		CriteriaBuilder cb = mock(CriteriaBuilder.class);
		Path<Object> rolePath = mock(Path.class);
		Path<Object> statusPath = mock(Path.class);
		Predicate rolePredicate = mock(Predicate.class);
		Predicate statusPredicate = mock(Predicate.class);
		Predicate combined = mock(Predicate.class);

		when(root.get("role")).thenReturn(rolePath);
		when(root.get("status")).thenReturn(statusPath);
		when(cb.equal(rolePath, UserRole.ADMIN)).thenReturn(rolePredicate);
		when(cb.equal(statusPath, UserStatus.ACTIVE)).thenReturn(statusPredicate);
		when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(combined);

		specCaptor.getValue().toPredicate(root, query, cb);

		verify(cb).equal(rolePath, UserRole.ADMIN);
		verify(cb).equal(statusPath, UserStatus.ACTIVE);
	}

	@Test
	void deleteUserRemovesOtherUserAndCredentials() {
		User user = trustedUser(UserStatus.PENDING);
		TrustedClientCredentials credentials = credentials(user, null, null, null);
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(credentialsRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(credentials));

		authService.deleteUser(user.getId(), UUID.randomUUID().toString());

		verify(credentialsRepository).delete(credentials);
		verify(userRepository).delete(user);
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
