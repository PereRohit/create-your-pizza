package com.createyourpizza.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.createyourpizza.auth.domain.User;
import com.createyourpizza.auth.domain.UserRole;

public interface UserRepository extends JpaRepository<User, UUID> {

	long countByRole(UserRole role);

	Optional<User> findByUsername(String username);
}
