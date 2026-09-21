package com.createyourpizza.auth.bootstrap;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.createyourpizza.auth.domain.User;
import com.createyourpizza.auth.domain.UserRole;
import com.createyourpizza.auth.domain.UserStatus;
import com.createyourpizza.auth.repository.UserRepository;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.countByRole(UserRole.ADMIN) >= 1) {
			return;
		}

		String username = generateUsername();
		String password = generatePassword();

		User admin = new User();
		admin.setRole(UserRole.ADMIN);
		admin.setStatus(UserStatus.ACTIVE);
		admin.setUsername(username);
		admin.setPasswordHash(passwordEncoder.encode(password));
		userRepository.save(admin);

		System.out.println("BOOTSTRAP ADMIN username=" + username + " password=" + password);
	}

	private static String generateUsername() {
		byte[] bytes = new byte[4];
		SECURE_RANDOM.nextBytes(bytes);
		return "admin-" + HexFormat.of().formatHex(bytes);
	}

	private static String generatePassword() {
		byte[] bytes = new byte[18];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
