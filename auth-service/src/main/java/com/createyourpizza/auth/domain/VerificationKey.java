package com.createyourpizza.auth.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "verification_keys")
@Getter
@Setter
@NoArgsConstructor
public class VerificationKey {

	@Id
	private String kid;

	@Column(nullable = false)
	private String alg;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "public_jwk", nullable = false)
	private Map<String, Object> publicJwk;

	@Column(nullable = false)
	private boolean active;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
