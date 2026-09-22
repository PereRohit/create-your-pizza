package com.createyourpizza.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.createyourpizza.catalog.domain.ComboItem;
import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.OptionKind;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.repository.ComboItemRepository;
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.ProductResponse;

@ExtendWith(MockitoExtension.class)
class CatalogResponseMapperTest {

	@Mock
	private ComboItemRepository comboItemRepository;

	private CatalogResponseMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = new CatalogResponseMapper(comboItemRepository);
	}

	@Test
	void pizzaBaseIncludesOptionsEnabledAndOmitsBlankNotes() {
		Product pizza = new Product();
		pizza.setId(UUID.randomUUID());
		pizza.setName("Margherita");
		pizza.setProductType(ProductType.pizza);
		pizza.setCategory(ProductCategory.VEG);
		pizza.setPrice(new BigDecimal("299.00"));
		pizza.setOptionsEnabled(true);
		pizza.setCustomisationNotes("  ");
		pizza.setActive(true);
		pizza.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		pizza.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

		ProductResponse response = mapper.toProductResponse(pizza);

		assertThat(response.getProductType()).isEqualTo("pizza-base");
		assertThat(response.getOptionsEnabled()).isTrue();
		assertThat(response.getCustomisationNotes()).isNull();
		assertThat(response.getProductCategory()).isEqualTo("veg");
	}

	@Test
	void comboIncludesSimpleIds() {
		UUID comboId = UUID.randomUUID();
		UUID simpleId = UUID.randomUUID();
		Product combo = new Product();
		combo.setId(comboId);
		combo.setName("Snack Combo");
		combo.setProductType(ProductType.combo);
		combo.setCategory(ProductCategory.NON_VEG);
		combo.setPrice(new BigDecimal("199.00"));
		combo.setActive(true);
		when(comboItemRepository.findByComboId(comboId)).thenReturn(List.of(new ComboItem(comboId, simpleId)));

		ProductResponse response = mapper.toProductResponse(combo);

		assertThat(response.getProductType()).isEqualTo("combo");
		assertThat(response.getSimpleIds()).containsExactly(simpleId);
		assertThat(response.getOptionsEnabled()).isNull();
	}

	@Test
	void simpleOmitsOptionsEnabled() {
		Product simple = new Product();
		simple.setId(UUID.randomUUID());
		simple.setName("Garlic Bread");
		simple.setProductType(ProductType.simple);
		simple.setCategory(ProductCategory.VEG);
		simple.setPrice(new BigDecimal("80.00"));
		simple.setActive(true);

		ProductResponse response = mapper.toProductResponse(simple);

		assertThat(response.getProductType()).isEqualTo("simple");
		assertThat(response.getOptionsEnabled()).isNull();
		assertThat(response.getSimpleIds()).isNull();
	}

	@Test
	void pizzaKeepsNonBlankCustomisationNotes() {
		Product pizza = new Product();
		pizza.setId(UUID.randomUUID());
		pizza.setName("Margherita");
		pizza.setProductType(ProductType.pizza);
		pizza.setCategory(ProductCategory.VEG);
		pizza.setPrice(new BigDecimal("299.00"));
		pizza.setOptionsEnabled(false);
		pizza.setCustomisationNotes("Extra basil");
		pizza.setActive(true);

		ProductResponse response = mapper.toProductResponse(pizza);

		assertThat(response.getOptionsEnabled()).isFalse();
		assertThat(response.getCustomisationNotes()).isEqualTo("Extra basil");
	}

	@Test
	void pizzaSpecHasNoCategoryAndUsesOptionPrice() {
		OptionEntity option = new OptionEntity();
		option.setId(UUID.randomUUID());
		option.setName("deep dish");
		option.setKind(OptionKind.CRUST_TYPE);
		option.setPrice(new BigDecimal("25.00"));
		option.setBase(false);
		option.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		option.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));

		OptionResponse response = mapper.toOptionResponse(option);

		assertThat(response.getProductType()).isEqualTo("pizza-spec");
		assertThat(response.getProductPrice()).isEqualByComparingTo("25.00");
		assertThat(response.getOptionKind()).isEqualTo("CRUST_TYPE");
		assertThat(response.isBase()).isFalse();
	}
}
