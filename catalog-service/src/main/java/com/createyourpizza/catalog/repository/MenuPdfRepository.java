package com.createyourpizza.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.createyourpizza.catalog.domain.MenuPdf;

public interface MenuPdfRepository extends JpaRepository<MenuPdf, Integer> {
}
