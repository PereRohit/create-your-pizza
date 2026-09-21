package com.createyourpizza.auth.service;

import java.util.List;

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
import com.createyourpizza.auth.web.dto.RegisterData;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

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

	private AccessTokenData accessToken(String token) {
		return new AccessTokenData(token, "Bearer", jwtProperties.getTtl().toSeconds());
	}
}
