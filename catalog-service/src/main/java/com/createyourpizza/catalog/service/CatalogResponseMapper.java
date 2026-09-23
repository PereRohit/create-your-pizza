package com.createyourpizza.catalog.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.repository.ComboItemRepository;
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.ProductResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CatalogResponseMapper {

	private final ComboItemRepository comboItemRepository;

	public ProductResponse toProductResponse(Product product) {
		List<UUID> simpleIds = null;
		String wireType = switch (product.getProductType()) {
			case simple -> "simple";
			case combo -> "combo";
			case pizza -> "pizza-base";
		};
		if (product.getProductType() == ProductType.combo) {
			simpleIds = comboItemRepository.findByComboId(product.getId()).stream()
					.map(c -> c.getSimpleId())
					.toList();
		}
		Boolean optionsEnabled = product.getProductType() == ProductType.pizza
				? Boolean.TRUE.equals(product.getOptionsEnabled())
				: null;
		String notes = product.getCustomisationNotes();
		if (notes != null && notes.isBlank()) {
			notes = null;
		}
		return ProductResponse.builder()
				.productId(product.getId())
				.productName(product.getName())
				.productType(wireType)
				.productCategory(product.getCategory().getDbValue())
				.productPrice(product.getPrice())
				.optionsEnabled(optionsEnabled)
				.customisationNotes(notes)
				.active(product.isActive())
				.simpleIds(simpleIds)
				.productCreatedAt(product.getCreatedAt())
				.productUpdatedAt(product.getUpdatedAt())
				.build();
	}

	public OptionResponse toOptionResponse(OptionEntity option) {
		return OptionResponse.builder()
				.productId(option.getId())
				.productName(option.getName())
				.productType("pizza-spec")
				.productPrice(option.getPrice())
				.optionKind(option.getKind().name())
				.isBase(option.isBase())
				.productCreatedAt(option.getCreatedAt())
				.productUpdatedAt(option.getUpdatedAt())
				.build();
	}
}
