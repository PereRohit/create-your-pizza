package com.createyourpizza.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.createyourpizza.auth.domain.VerificationKey;

public interface VerificationKeyRepository extends JpaRepository<VerificationKey, String> {

	List<VerificationKey> findByActiveTrue();
}
