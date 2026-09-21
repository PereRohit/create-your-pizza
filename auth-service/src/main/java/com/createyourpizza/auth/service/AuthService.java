package com.createyourpizza.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.createyourpizza.auth.web.dto.AdminData;
import com.createyourpizza.auth.web.dto.ApproveData;
import com.createyourpizza.auth.web.dto.Pagination;
import com.createyourpizza.auth.web.dto.RegisterData;
import com.createyourpizza.auth.web.dto.UserListItem;
import com.createyourpizza.auth.web.dto.UserStatusData;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 100;

	private final UserRepository userRepository;
	private final TrustedClientCredentialsRepository credentialsRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtIssuer jwtIssuer;
	private final JwtProperties jwtProperties;

	@Transactional(readOnly = true)
	public AccessTokenData login(String username, String password) {
		User user = userRepository.findByUsername(username)
				.orElseThrow(() -> new AuthFailureException("Invalid credentials"));

		if (user.getRole() != UserRole.ADMIN
				|| user.getStatus() != UserStatus.ACTIVE
				|| user.getPasswordHash() == null
				|| !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new AuthFailureException("Invalid credentials");
		}

		String token = jwtIssuer.issue(
				user.getId().toString(),
				List.of(UserRole.ADMIN.name()),
				AuthScopes.ADMIN,
				null);
		return accessToken(token);
	}

	@Transactional
	public RegisterData register(String displayName) {
		User user = new User();
		user.setRole(UserRole.TRUSTED_SYSTEM);
		user.setStatus(UserStatus.PENDING);
		user.setDisplayName(displayName);
		userRepository.save(user);

		TrustedClientCredentials credentials = new TrustedClientCredentials();
		credentials.setUser(user);
		credentialsRepository.save(credentials);

		return new RegisterData(user.getId(), user.getStatus(), user.getRole());
	}

	@Transactional(readOnly = true)
	public AccessTokenData exchangeToken(String apiKey, String apiSecret) {
		TrustedClientCredentials credentials = credentialsRepository.findByApiKey(apiKey)
				.orElseThrow(() -> new AuthFailureException("Invalid credentials"));

		User user = credentials.getUser();
		if (user.getRole() != UserRole.TRUSTED_SYSTEM
				|| user.getStatus() != UserStatus.ACTIVE
				|| credentials.getRevokedAt() != null
				|| credentials.getSecretHash() == null
				|| !passwordEncoder.matches(apiSecret, credentials.getSecretHash())) {
			throw new AuthFailureException("Invalid credentials");
		}

		String token = jwtIssuer.issue(
				user.getId().toString(),
				List.of(UserRole.TRUSTED_SYSTEM.name()),
				AuthScopes.TRUSTED,
				credentials.getApiKey());
		return accessToken(token);
	}

	@Transactional
	public AdminData createAdmin(String username, String password) {
		if (userRepository.existsByUsername(username)) {
			throw new AuthConflictException("Username already exists");
		}

		User admin = new User();
		admin.setRole(UserRole.ADMIN);
		admin.setStatus(UserStatus.ACTIVE);
		admin.setUsername(username);
		admin.setPasswordHash(passwordEncoder.encode(password));
		userRepository.save(admin);

		return new AdminData(admin.getId(), admin.getRole(), admin.getStatus(), admin.getUsername());
	}

	@Transactional(readOnly = true)
	public UserListPage listUsers(UserRole role, UserStatus status, Integer page, Integer size) {
		int currentPage = page == null || page < 1 ? 1 : page;
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

		Specification<User> spec = (root, query, cb) -> null;
		if (role != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
		}
		if (status != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
		}

		Page<User> result = userRepository.findAll(
				spec,
				PageRequest.of(currentPage - 1, pageSize, Sort.by(Sort.Direction.ASC, "createdAt")));

		List<UserListItem> items = result.getContent().stream()
				.map(user -> new UserListItem(
						user.getId(),
						user.getRole(),
						user.getStatus(),
						user.getUsername(),
						user.getDisplayName()))
				.toList();

		int next = result.hasNext() ? currentPage + 1 : -1;
		Pagination pagination = new Pagination(currentPage, next, result.getTotalElements());
		return new UserListPage(items, pagination);
	}

	@Transactional
	public ApproveData approve(UUID userId) {
		User user = requireTrusted(userId);
		if (user.getStatus() != UserStatus.PENDING) {
			throw new AuthBadRequestException("User is not pending approval");
		}

		TrustedClientCredentials credentials = credentialsRepository.findByUser_Id(userId)
				.orElseThrow(() -> new AuthNotFoundException("Credentials not found"));

		String apiKey = generateCredential("key");
		String apiSecret = generateCredential("secret");
		credentials.setApiKey(apiKey);
		credentials.setSecretHash(passwordEncoder.encode(apiSecret));
		credentials.setRevokedAt(null);
		credentialsRepository.save(credentials);

		user.setStatus(UserStatus.ACTIVE);
		userRepository.save(user);

		return new ApproveData(user.getId(), user.getStatus(), apiKey, apiSecret);
	}

	@Transactional
	public UserStatusData deny(UUID userId) {
		User user = requireTrusted(userId);
		if (user.getStatus() != UserStatus.PENDING) {
			throw new AuthBadRequestException("User is not pending approval");
		}

		user.setStatus(UserStatus.DENIED);
		userRepository.save(user);
		return new UserStatusData(user.getId(), user.getRole(), user.getStatus());
	}

	@Transactional
	public UserStatusData revoke(UUID userId) {
		User user = requireTrusted(userId);
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new AuthBadRequestException("User is not active");
		}

		TrustedClientCredentials credentials = credentialsRepository.findByUser_Id(userId)
				.orElseThrow(() -> new AuthNotFoundException("Credentials not found"));
		credentials.setRevokedAt(Instant.now());
		credentialsRepository.save(credentials);

		user.setStatus(UserStatus.REVOKED);
		userRepository.save(user);
		return new UserStatusData(user.getId(), user.getRole(), user.getStatus());
	}

	@Transactional
	public void deleteUser(UUID userId, String callerSub) {
		if (callerSub != null && callerSub.equals(userId.toString())) {
			throw new AuthForbiddenException("Cannot delete self");
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new AuthNotFoundException("User not found"));

		credentialsRepository.findByUser_Id(userId).ifPresent(credentialsRepository::delete);
		userRepository.delete(user);
	}

	private User requireTrusted(UUID userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new AuthNotFoundException("User not found"));
		if (user.getRole() != UserRole.TRUSTED_SYSTEM) {
			throw new AuthBadRequestException("User is not a trusted system");
		}
		return user;
	}

	private AccessTokenData accessToken(String token) {
		return new AccessTokenData(token, "Bearer", jwtProperties.getTtl().toSeconds());
	}

	private static String generateCredential(String prefix) {
		byte[] bytes = new byte[24];
		SECURE_RANDOM.nextBytes(bytes);
		return prefix + "-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record UserListPage(List<UserListItem> items, Pagination pagination) {
	}
}
