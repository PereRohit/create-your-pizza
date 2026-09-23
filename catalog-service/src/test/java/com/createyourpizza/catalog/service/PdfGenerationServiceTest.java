package com.createyourpizza.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.createyourpizza.catalog.config.LockProperties;
import com.createyourpizza.catalog.domain.CatalogMeta;
import com.createyourpizza.catalog.domain.MenuPdf;
import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.OptionKind;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.domain.ProductCategory;
import com.createyourpizza.catalog.domain.ProductType;
import com.createyourpizza.catalog.lock.CatalogLockKeys;
import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.lock.InMemoryCatalogLockStore;
import com.createyourpizza.catalog.menu.InMemoryLatestMenuStore;
import com.createyourpizza.catalog.menu.LatestMenu;
import com.createyourpizza.catalog.pdf.MenuPdfModel;
import com.createyourpizza.catalog.pdf.MenuPdfRenderer;
import com.createyourpizza.catalog.repository.CatalogMetaRepository;
import com.createyourpizza.catalog.repository.MenuPdfRepository;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.seed.CatalogMetaDefaults;

@ExtendWith(MockitoExtension.class)
class PdfGenerationServiceTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private OptionEntityRepository optionEntityRepository;

	@Mock
	private CatalogMetaRepository catalogMetaRepository;

	@Mock
	private MenuPdfRepository menuPdfRepository;

	private InMemoryCatalogLockStore lockStore;
	private InMemoryLatestMenuStore latestMenuStore;
	private RecordingMenuPdfRenderer renderer;
	private CatalogMeta meta;
	private PdfGenerationService service;

	@BeforeEach
	void setUp() {
		lockStore = new InMemoryCatalogLockStore();
		latestMenuStore = new InMemoryLatestMenuStore();
		renderer = new RecordingMenuPdfRenderer();
		meta = CatalogMetaDefaults.newSeedRow(NOW);
		service = newService(lockStore);
	}

	@Test
	void writeLockHeldSkipsWithoutTouchingDirtyOrHistory() {
		lockStore.tryAcquire(CatalogLockKeys.CATALOG_WRITE, "writer", Duration.ofSeconds(30));

		service.runOnce();

		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();
		assertThat(latestMenuStore.getLatest()).isNull();
		assertThat(renderer.last).isNull();
		verify(catalogMetaRepository, never()).save(any());
		verify(menuPdfRepository, never()).save(any());
	}

	@Test
	void notDirtyIsNoOp() {
		meta.setDirty(false);
		when(catalogMetaRepository.findById(CatalogMetaDefaults.META_ID)).thenReturn(Optional.of(meta));

		service.runOnce();

		assertThat(meta.isDirty()).isFalse();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(latestMenuStore.getLatest()).isNull();
		assertThat(renderer.last).isNull();
		verify(menuPdfRepository, never()).save(any());
	}

	@Test
	void pdfLockNotAcquiredSkips() {
		lockStore.tryAcquire(CatalogLockKeys.PDF_GENERATION, "other-job", Duration.ofSeconds(120));
		when(catalogMetaRepository.findById(CatalogMetaDefaults.META_ID)).thenReturn(Optional.of(meta));

		service.runOnce();

		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(latestMenuStore.getLatest()).isNull();
		verify(menuPdfRepository, never()).save(any());
	}

	@Test
	void writeLockAfterPdfAcquireReleasesPdfLockAndSkips() {
		RecheckWriteLockStore recheckLocks = new RecheckWriteLockStore();
		service = newService(recheckLocks);
		when(catalogMetaRepository.findById(CatalogMetaDefaults.META_ID)).thenReturn(Optional.of(meta));

		service.runOnce();

		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(recheckLocks.pdfHeld).isFalse();
		assertThat(latestMenuStore.getLatest()).isNull();
		assertThat(renderer.last).isNull();
		verify(menuPdfRepository, never()).save(any());
	}

	@Test
	void successInsertsV1WritesRedisAndClearsDirty() {
		stubSuccessfulGenerate();

		service.runOnce();

		assertThat(meta.isDirty()).isFalse();
		assertThat(meta.getLastPdfVersion()).isEqualTo(1);
		assertThat(lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();

		ArgumentCaptor<MenuPdf> saved = ArgumentCaptor.forClass(MenuPdf.class);
		verify(menuPdfRepository).save(saved.capture());
		assertThat(saved.getValue().getVersion()).isEqualTo(1);
		assertThat(saved.getValue().getPdf()).isEqualTo(renderer.bytes);
		assertThat(saved.getValue().getGeneratedAt()).isEqualTo(NOW);

		LatestMenu latest = latestMenuStore.getLatest();
		assertThat(latest).isNotNull();
		assertThat(latest.version()).isEqualTo(1);
		assertThat(latest.pdf()).isEqualTo(renderer.bytes);
		assertThat(latest.updatedAt()).isEqualTo(NOW);
	}

	@Test
	void secondSuccessIncrementsOnlyAfterInsert() {
		stubSuccessfulGenerate();
		service.runOnce();
		meta.setDirty(true);

		service.runOnce();

		assertThat(meta.getLastPdfVersion()).isEqualTo(2);
		ArgumentCaptor<MenuPdf> saved = ArgumentCaptor.forClass(MenuPdf.class);
		verify(menuPdfRepository, org.mockito.Mockito.times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(m -> m.getVersion()).containsExactly(1, 2);
		assertThat(latestMenuStore.getLatest().version()).isEqualTo(2);
	}

	@Test
	void renderFailureDoesNotBumpVersionAndReleasesPdfLock() {
		when(catalogMetaRepository.findById(CatalogMetaDefaults.META_ID)).thenReturn(Optional.of(meta));
		when(productRepository.findByActiveTrue()).thenReturn(List.of());
		when(optionEntityRepository.findAll()).thenReturn(List.of());
		renderer.fail = new IllegalStateException("render boom");

		assertThatThrownBy(() -> service.runOnce()).isInstanceOf(IllegalStateException.class);

		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();
		assertThat(latestMenuStore.getLatest()).isNull();
		verify(menuPdfRepository, never()).save(any());
	}

	@Test
	void persistFailureDoesNotBumpVersionAndReleasesPdfLock() {
		when(catalogMetaRepository.findById(CatalogMetaDefaults.META_ID)).thenReturn(Optional.of(meta));
		when(productRepository.findByActiveTrue()).thenReturn(List.of());
		when(optionEntityRepository.findAll()).thenReturn(List.of());
		when(transactionTemplate.execute(any())).thenThrow(new IllegalStateException("db boom"));

		assertThatThrownBy(() -> service.runOnce()).isInstanceOf(IllegalStateException.class);

		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();
		assertThat(latestMenuStore.getLatest()).isNull();
	}

	@Test
	void contentModelHasHeaderVersionOptionsNoteAndPizzaSpecOwnSpace() {
		stubSuccessfulGenerate();
		Product pizzaOn = product("Margherita", ProductType.pizza, "299.00", true, Instant.parse("2026-01-01T00:00:01Z"));
		Product pizzaOff = product("Plain Base", ProductType.pizza, "199.00", false, Instant.parse("2026-01-01T00:00:02Z"));
		Product simple = product("Garlic Bread", ProductType.simple, "80.00", false, Instant.parse("2026-01-01T00:00:00Z"));
		OptionEntity olive = option("olive", "15.00");
		when(productRepository.findByActiveTrue()).thenReturn(List.of(pizzaOn, pizzaOff, simple));
		when(optionEntityRepository.findAll()).thenReturn(List.of(olive));

		service.runOnce();

		assertThat(renderer.last.header()).isEqualTo(MenuPdfModel.HEADER);
		assertThat(renderer.last.version()).isEqualTo(1);
		assertThat(renderer.last.sellable()).containsExactly(
				new MenuPdfModel.SellableRow("Garlic Bread", new BigDecimal("80.00"), false),
				new MenuPdfModel.SellableRow("Margherita", new BigDecimal("299.00"), true),
				new MenuPdfModel.SellableRow("Plain Base", new BigDecimal("199.00"), false));
		assertThat(renderer.last.sellable().get(1).optionsAvailable()).isTrue();
		assertThat(renderer.last.sellable().get(2).optionsAvailable()).isFalse();
		assertThat(renderer.last.pizzaSpec()).containsExactly(
				new MenuPdfModel.OptionRow("olive", new BigDecimal("15.00")));
	}

	private void stubSuccessfulGenerate() {
		when(catalogMetaRepository.findById(CatalogMetaDefaults.META_ID)).thenReturn(Optional.of(meta));
		when(productRepository.findByActiveTrue()).thenReturn(List.of());
		when(optionEntityRepository.findAll()).thenReturn(List.of());
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(null);
		});
		when(menuPdfRepository.save(any(MenuPdf.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(catalogMetaRepository.save(any(CatalogMeta.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private PdfGenerationService newService(CatalogLockStore locks) {
		return new PdfGenerationService(
				locks,
				new LockProperties(),
				transactionTemplate,
				productRepository,
				optionEntityRepository,
				catalogMetaRepository,
				menuPdfRepository,
				renderer,
				latestMenuStore,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static Product product(String name, ProductType type, String price, boolean optionsEnabled, Instant createdAt) {
		Product product = new Product();
		product.setId(UUID.randomUUID());
		product.setName(name);
		product.setProductType(type);
		product.setCategory(ProductCategory.VEG);
		product.setPrice(new BigDecimal(price));
		product.setOptionsEnabled(optionsEnabled);
		product.setActive(true);
		product.setCreatedAt(createdAt);
		return product;
	}

	private static OptionEntity option(String name, String price) {
		OptionEntity option = new OptionEntity();
		option.setId(UUID.randomUUID());
		option.setKind(OptionKind.TOPPING);
		option.setName(name);
		option.setPrice(new BigDecimal(price));
		option.setCreatedAt(NOW);
		return option;
	}

	private static final class RecordingMenuPdfRenderer implements MenuPdfRenderer {

		private final byte[] bytes = "PDF".getBytes(StandardCharsets.UTF_8);

		private MenuPdfModel last;

		private RuntimeException fail;

		@Override
		public byte[] render(MenuPdfModel model) {
			this.last = model;
			if (fail != null) {
				throw fail;
			}
			return bytes;
		}
	}

	/**
	 * First write-lock check is clear; after the PDF lock is acquired the write lock appears.
	 */
	private static final class RecheckWriteLockStore implements CatalogLockStore {

		private boolean pdfHeld;

		@Override
		public boolean isHeld(String key) {
			if (CatalogLockKeys.CATALOG_WRITE.equals(key)) {
				return pdfHeld;
			}
			return CatalogLockKeys.PDF_GENERATION.equals(key) && pdfHeld;
		}

		@Override
		public boolean tryAcquire(String key, String holder, Duration ttl) {
			if (CatalogLockKeys.PDF_GENERATION.equals(key) && !pdfHeld) {
				pdfHeld = true;
				return true;
			}
			return false;
		}

		@Override
		public void release(String key) {
			if (CatalogLockKeys.PDF_GENERATION.equals(key)) {
				pdfHeld = false;
			}
		}
	}
}
