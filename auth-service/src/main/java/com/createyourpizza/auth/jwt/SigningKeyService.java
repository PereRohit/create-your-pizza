package com.createyourpizza.auth.jwt;

import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.createyourpizza.auth.domain.VerificationKey;
import com.createyourpizza.auth.repository.VerificationKeyRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Service
@Order(1)
@RequiredArgsConstructor
public class SigningKeyService implements ApplicationRunner {

	private final VerificationKeyRepository verificationKeyRepository;

	@Getter
	private RSAKey signingKey;

	@Override
	public void run(ApplicationArguments args) throws JOSEException {
		ensureSigningKey();
	}

	public synchronized void ensureSigningKey() throws JOSEException {
		if (signingKey != null) {
			return;
		}
		RSAKey key = new RSAKeyGenerator(2048)
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyIDFromThumbprint(true)
				.generate();

		RSAKey publicOnly = key.toPublicJWK();
		Map<String, Object> publicJwk = publicOnly.toJSONObject();

		VerificationKey row = verificationKeyRepository.findById(key.getKeyID())
				.orElseGet(VerificationKey::new);
		row.setKid(key.getKeyID());
		row.setAlg(JWSAlgorithm.RS256.getName());
		row.setPublicJwk(publicJwk);
		row.setActive(true);
		verificationKeyRepository.save(row);

		this.signingKey = key;
	}

	public String kid() {
		return signingKey.getKeyID();
	}
}
