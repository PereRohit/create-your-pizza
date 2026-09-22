package com.createyourpizza.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.createyourpizza.catalog.domain.CatalogMeta;

public interface CatalogMetaRepository extends JpaRepository<CatalogMeta, Short> {
}
