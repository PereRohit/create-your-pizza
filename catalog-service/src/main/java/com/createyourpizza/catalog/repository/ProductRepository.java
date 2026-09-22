package com.createyourpizza.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.createyourpizza.catalog.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}
