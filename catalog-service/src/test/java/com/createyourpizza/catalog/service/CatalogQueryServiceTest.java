package com.createyourpizza.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.createyourpizza.catalog.cache.InMemoryCatalogCacheStore;
import com.createyourpizza.catalog.config.CacheProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.OptionKind;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.repository.ComboItemRepository;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.web.dto.OptionResponse;
import com.createyourpizza.catalog.web.dto.ProductResponse;

@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private OptionEntityRepository optionEntityRepository;

	@Mock
	private ComboItemRepository comboItemRepository;

	private CatalogQueryService service;

	@BeforeEach
	void setUp() {
		CacheProperties cacheProperties = new CacheProperties();
		cacheProperties.setCatalogTtl(Duration.ofMinutes(3));
		service = new CatalogQueryService(
				productRepository,
				optionEntityRepository,
				new CatalogResponseMapper(comboItemRepository),
				new InMemoryCatalogCacheStore(Clock.systemUTC()),
				cacheProperties);
	}

	@Test
	void untypedListUnionsProductsAndOptionsSortedByCreatedAt() {
		Product later = product("Later", Instant.parse("2026-01-02T00:00:00Z"), ProductType.simple);
		Product earlier = product("Earlier", Instant.parse("2026-01-01T00:00:00Z"), ProductType.simple);
		OptionEntity option = option("Thin", Instant.parse("2026-01-01T12:00:00Z"), "15.00");
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(later, earlier));
		when(optionEntityRepository.findAll(any(Specification.class))).thenReturn(List.of(option));

		CatalogQueryService.CatalogListPage page = service.list(null, null, null, null, null);

		assertThat(page.items()).hasSize(3);
		assertThat(((ProductResponse) page.items().get(0)).getProductName()).isEqualTo("Earlier");
		assertThat(((OptionResponse) page.items().get(1)).getProductName()).isEqualTo("Thin");
		assertThat(((ProductResponse) page.items().get(2)).getProductName()).isEqualTo("Later");
		assertThat(page.pagination().current()).isEqualTo(1);
		assertThat(page.pagination().next()).isEqualTo(-1);
		assertThat(page.pagination().total()).isEqualTo(3);
	}

	@Test
	void categoryOnUntypedListExcludesPizzaSpec() {
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(
				product("Veg", Instant.parse("2026-01-01T00:00:00Z"), ProductType.simple)));

		CatalogQueryService.CatalogListPage veg = service.list("veg", null, null, 1, 10);

		assertThat(veg.items()).hasSize(1);
		verify(optionEntityRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void typePizzaSpecIgnoresCategoryAndSkipsProducts() {
		when(optionEntityRepository.findAll(any(Specification.class))).thenReturn(List.of(
				option("Olive", Instant.parse("2026-01-01T00:00:00Z"), "15.00")));

		CatalogQueryService.CatalogListPage specs = service.list("veg", "pizza-spec", null, 1, 10);

		assertThat(specs.items()).hasSize(1);
		assertThat(((OptionResponse) specs.items().getFirst()).getProductType()).isEqualTo("pizza-spec");
		verify(productRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void typedListSelectsOnlyThatSource() {
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(
				product("Pie", Instant.parse("2026-01-01T00:00:00Z"), ProductType.pizza)));

		CatalogQueryService.CatalogListPage bases = service.list(null, "pizza-base", null, 1, 10);

		assertThat(bases.items()).hasSize(1);
		assertThat(((ProductResponse) bases.items().getFirst()).getProductType()).isEqualTo("pizza-base");
		verify(optionEntityRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void comboAndSimpleTypesQueryProductsOnly() {
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of());

		service.list(null, "combo", null, 1, 10);
		service.list(null, "simple", null, 1, 10);

		verify(optionEntityRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void size200IsClampedTo100AndStillReturnsPage() {
		List<Product> products = new ArrayList<>();
		Instant start = Instant.parse("2026-01-01T00:00:00Z");
		for (int i = 0; i < 101; i++) {
			products.add(product("P" + i, start.plusSeconds(i), ProductType.simple));
		}
		when(productRepository.findAll(any(Specification.class))).thenReturn(products);

		CatalogQueryService.CatalogListPage page1 = service.list(null, "simple", null, 1, 200);
		assertThat(page1.items()).hasSize(100);
		assertThat(page1.pagination().current()).isEqualTo(1);
		assertThat(page1.pagination().next()).isEqualTo(2);
		assertThat(page1.pagination().total()).isEqualTo(101);

		CatalogQueryService.CatalogListPage page2 = service.list(null, "simple", null, 2, 200);
		assertThat(page2.items()).hasSize(1);
		assertThat(page2.pagination().next()).isEqualTo(-1);
	}

	@Test
	void defaultPageSizeIs10() {
		List<Product> products = new ArrayList<>();
		Instant start = Instant.parse("2026-01-01T00:00:00Z");
		for (int i = 0; i < 12; i++) {
			products.add(product("P" + i, start.plusSeconds(i), ProductType.simple));
		}
		when(productRepository.findAll(any(Specification.class))).thenReturn(products);

		CatalogQueryService.CatalogListPage page = service.list(null, "simple", null, null, null);

		assertThat(page.items()).hasSize(10);
		assertThat(page.pagination().next()).isEqualTo(2);
		assertThat(page.pagination().total()).isEqualTo(12);
	}

	@Test
	void getProductAndOption404WhenMissing() {
		UUID id = UUID.randomUUID();
		when(productRepository.findById(id)).thenReturn(Optional.empty());
		when(optionEntityRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getProduct(id)).isInstanceOf(CatalogNotFoundException.class);
		assertThatThrownBy(() -> service.getOption(id)).isInstanceOf(CatalogNotFoundException.class);
	}

	@Test
	void unknownTypeAndCategoryAreBadRequest() {
		assertThatThrownBy(() -> service.list(null, "dessert", null, 1, 10))
				.isInstanceOf(CatalogBadRequestException.class);
		assertThatThrownBy(() -> service.list("vegan", null, null, 1, 10))
				.isInstanceOf(CatalogBadRequestException.class);
	}

	@Test
	void maxPriceIsPassedThroughToBothSourcesOnUntypedList() {
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of());
		when(optionEntityRepository.findAll(any(Specification.class))).thenReturn(List.of());

		service.list(null, null, new BigDecimal("100.00"), 1, 10);

		verify(productRepository).findAll(any(Specification.class));
		verify(optionEntityRepository).findAll(any(Specification.class));
	}

	@Test
	void getProductMapsSellableWireType() {
		Product pizza = product("Margherita", Instant.parse("2026-01-01T00:00:00Z"), ProductType.pizza);
		pizza.setOptionsEnabled(true);
		pizza.setCustomisationNotes("Extra basil");
		when(productRepository.findById(pizza.getId())).thenReturn(Optional.of(pizza));

		ProductResponse response = service.getProduct(pizza.getId());

		assertThat(response.getProductType()).isEqualTo("pizza-base");
		assertThat(response.getOptionsEnabled()).isTrue();
		assertThat(response.getCustomisationNotes()).isEqualTo("Extra basil");
	}

	@Test
	void getOptionMapsPizzaSpec() {
		OptionEntity option = option("Olive", Instant.parse("2026-01-01T00:00:00Z"), "15.00");
		when(optionEntityRepository.findById(option.getId())).thenReturn(Optional.of(option));

		OptionResponse response = service.getOption(option.getId());

		assertThat(response.getProductType()).isEqualTo("pizza-spec");
		assertThat(response.getProductName()).isEqualTo("Olive");
	}

	@Test
	void blankFiltersAreUntypedAndPageBelowOneBecomesFirstPage() {
		when(productRepository.findAll(any(Specification.class))).thenReturn(List.of());
		when(optionEntityRepository.findAll(any(Specification.class))).thenReturn(List.of());

		CatalogQueryService.CatalogListPage page = service.list("  ", "  ", null, 0, 0);

		assertThat(page.pagination().current()).isEqualTo(1);
		assertThat(page.items()).isEmpty();
		assertThat(page.pagination().next()).isEqualTo(-1);
	}

	private static Product product(String name, Instant created, ProductType type) {
		Product product = new Product();
		product.setId(UUID.randomUUID());
		product.setName(name);
		product.setProductType(type);
		product.setCategory(ProductCategory.VEG);
		product.setPrice(new BigDecimal("99.00"));
		product.setActive(true);
		product.setCreatedAt(created);
		product.setUpdatedAt(created);
		return product;
	}

	private static OptionEntity option(String name, Instant created, String price) {
		OptionEntity option = new OptionEntity();
		option.setId(UUID.randomUUID());
		option.setName(name);
		option.setKind(OptionKind.TOPPING);
		option.setPrice(new BigDecimal(price));
		option.setBase(true);
		option.setCreatedAt(created);
		option.setUpdatedAt(created);
		return option;
	}
}
