package com.createyourpizza.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.createyourpizza.catalog.domain.ComboItem;
import com.createyourpizza.catalog.domain.ComboItemId;

public interface ComboItemRepository extends JpaRepository<ComboItem, ComboItemId> {

	List<ComboItem> findByComboId(UUID comboId);

	void deleteByComboId(UUID comboId);

	void deleteBySimpleId(UUID simpleId);
}
