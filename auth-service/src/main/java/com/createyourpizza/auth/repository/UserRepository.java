package com.createyourpizza.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.createyourpizza.auth.domain.User;
import com.createyourpizza.auth.domain.UserRole;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

	long countByRole(UserRole role);

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);
}
