package com.createyourpizza.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import com.createyourpizza.auth.domain.VerificationKey;
import com.createyourpizza.auth.repository.VerificationKeyRepository;
import com.nimbusds.jose.JWSAlgorithm;

@ExtendWith(MockitoExtension.class)
class SigningKeyServiceTest {

	@Mock
	private VerificationKeyRepository verificationKeyRepository;

	@InjectMocks
	private SigningKeyService signingKeyService;

	@Test
	void onBootGeneratesRs256KeyAndUpsertsPublicJwkOnly() throws Exception {
		when(verificationKeyRepository.findById(any())).thenReturn(Optional.empty());
		when(verificationKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		signingKeyService.run(new DefaultApplicationArguments());

		ArgumentCaptor<VerificationKey> captor = ArgumentCaptor.forClass(VerificationKey.class);
		verify(verificationKeyRepository).save(captor.capture());

		VerificationKey saved = captor.getValue();
		assertThat(saved.getKid()).isNotBlank();
		assertThat(saved.getAlg()).isEqualTo(JWSAlgorithm.RS256.getName());
		assertThat(saved.isActive()).isTrue();

		Map<String, Object> jwk = saved.getPublicJwk();
		assertThat(jwk).containsEntry("kty", "RSA");
		assertThat(jwk).containsKey("n");
		assertThat(jwk).containsKey("e");
		assertThat(jwk).containsEntry("kid", saved.getKid());
		assertThat(jwk).doesNotContainKey("d");
		assertThat(jwk).doesNotContainKey("p");
		assertThat(jwk).doesNotContainKey("q");
		assertThat(jwk).doesNotContainKey("dp");
		assertThat(jwk).doesNotContainKey("dq");
		assertThat(jwk).doesNotContainKey("qi");

		assertThat(signingKeyService.getSigningKey().toPrivateKey()).isNotNull();
		assertThat(signingKeyService.kid()).isEqualTo(saved.getKid());
	}
}
