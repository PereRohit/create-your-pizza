package com.createyourpizza.auth.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.createyourpizza.auth.repository.VerificationKeyRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class JwksController {

	private final VerificationKeyRepository verificationKeyRepository;

	@GetMapping(path = "/auth/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, List<Map<String, Object>>> jwks() {
		List<Map<String, Object>> keys = verificationKeyRepository.findByActiveTrue().stream()
				.map(k -> k.getPublicJwk())
				.toList();
		return Map.of("keys", keys);
	}
}
