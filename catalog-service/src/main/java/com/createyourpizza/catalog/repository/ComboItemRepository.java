package com.createyourpizza.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.createyourpizza.catalog.domain.ComboItem;
import com.createyourpizza.catalog.domain.ComboItemId;

public interface ComboItemRepository extends JpaRepository<ComboItem, ComboItemId> {
}
