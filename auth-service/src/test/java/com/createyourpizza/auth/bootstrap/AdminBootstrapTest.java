package com.createyourpizza.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.createyourpizza.auth.domain.User;
import com.createyourpizza.auth.domain.UserRole;
import com.createyourpizza.auth.domain.UserStatus;
import com.createyourpizza.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

	@Mock
	private UserRepository userRepository;

	@Spy
	private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@InjectMocks
	private AdminBootstrap bootstrap;

	private PrintStream originalOut;
	private ByteArrayOutputStream capturedOut;

	@BeforeEach
	void captureStdout() {
		originalOut = System.out;
		capturedOut = new ByteArrayOutputStream();
		System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
	}

	@AfterEach
	void restoreStdout() {
		System.setOut(originalOut);
	}

	@Test
	void createsAdminAndPrintsCredentialsWhenNoAdminsExist() {
		when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(0L);

		bootstrap.run(new DefaultApplicationArguments());

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());

		User saved = captor.getValue();
		assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(saved.getUsername()).matches("^admin-[0-9a-f]{8}$");
		assertThat(saved.getPasswordHash()).isNotBlank();

		String stdout = capturedOut.toString(StandardCharsets.UTF_8);
		assertThat(stdout).contains("BOOTSTRAP ADMIN username=" + saved.getUsername());
		assertThat(stdout).contains("password=");

		String password = extractPassword(stdout);
		assertThat(password).hasSize(24);
		assertThat(passwordEncoder.matches(password, saved.getPasswordHash())).isTrue();
	}

	@Test
	void skipsBootstrapWhenAdminAlreadyExists() {
		when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

		bootstrap.run(new DefaultApplicationArguments());

		verify(userRepository, never()).save(any());
		assertThat(capturedOut.toString(StandardCharsets.UTF_8)).isEmpty();
	}

	private static String extractPassword(String stdout) {
		int idx = stdout.indexOf("password=");
		assertThat(idx).isGreaterThanOrEqualTo(0);
		return stdout.substring(idx + "password=".length()).trim();
	}
}
