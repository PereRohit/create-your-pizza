package com.createyourpizza.catalog.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.createyourpizza.catalog.config.LockProperties;
import com.createyourpizza.catalog.domain.CatalogMeta;
import com.createyourpizza.catalog.domain.MenuPdf;
import com.createyourpizza.catalog.domain.OptionEntity;
import com.createyourpizza.catalog.domain.Product;
import com.createyourpizza.catalog.lock.CatalogLockKeys;
import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.menu.LatestMenu;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.pdf.MenuPdfModel;
import com.createyourpizza.catalog.pdf.MenuPdfRenderer;
import com.createyourpizza.catalog.repository.CatalogMetaRepository;
import com.createyourpizza.catalog.repository.MenuPdfRepository;
import com.createyourpizza.catalog.repository.OptionEntityRepository;
import com.createyourpizza.catalog.repository.ProductRepository;
import com.createyourpizza.catalog.seed.CatalogMetaDefaults;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PdfGenerationService {

	private final CatalogLockStore lockStore;
	private final LockProperties lockProperties;
	private final TransactionTemplate transactionTemplate;
	private final ProductRepository productRepository;
	private final OptionEntityRepository optionEntityRepository;
	private final CatalogMetaRepository catalogMetaRepository;
	private final MenuPdfRepository menuPdfRepository;
	private final MenuPdfRenderer renderer;
	private final LatestMenuStore latestMenuStore;
	private final Clock clock;

	public void runOnce() {
		if (lockStore.isHeld(CatalogLockKeys.CATALOG_WRITE)) {
			return;
		}
		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElse(null);
		if (meta == null || !meta.isDirty()) {
			return;
		}
		String holder = UUID.randomUUID().toString();
		if (!lockStore.tryAcquire(CatalogLockKeys.PDF_GENERATION, holder, lockProperties.getPdfTtl())) {
			return;
		}
		try {
			if (lockStore.isHeld(CatalogLockKeys.CATALOG_WRITE)) {
				return;
			}
			generateAndPersist(meta);
		}
		finally {
			lockStore.release(CatalogLockKeys.PDF_GENERATION);
		}
	}

	private void generateAndPersist(CatalogMeta meta) {
		int version = nextVersion(meta.getLastPdfVersion());
		MenuPdfModel model = buildModel(version);
		byte[] pdf = renderer.render(model);
		Instant now = clock.instant();
		transactionTemplate.execute(status -> {
			MenuPdf row = new MenuPdf();
			row.setVersion(version);
			row.setPdf(pdf);
			row.setGeneratedAt(now);
			menuPdfRepository.save(row);
			meta.setDirty(false);
			meta.setLastPdfVersion(version);
			catalogMetaRepository.save(meta);
			return null;
		});
		latestMenuStore.put(new LatestMenu(pdf, version, now));
	}

	private MenuPdfModel buildModel(int version) {
		List<MenuPdfModel.SellableRow> sellable = productRepository.findByActiveTrue().stream()
				.sorted(Comparator.comparing((Product p) -> p.getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(p -> p.getName(), Comparator.nullsLast(Comparator.naturalOrder())))
				.map(product -> new MenuPdfModel.SellableRow(
						product.getName(),
						product.getPrice(),
						Boolean.TRUE.equals(product.getOptionsEnabled())))
				.toList();
		List<MenuPdfModel.OptionRow> pizzaSpec = optionEntityRepository.findAll().stream()
				.sorted(Comparator.comparing((OptionEntity o) -> o.getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(o -> o.getName(), Comparator.nullsLast(Comparator.naturalOrder())))
				.map(option -> new MenuPdfModel.OptionRow(option.getName(), option.getPrice()))
				.toList();
		return new MenuPdfModel(MenuPdfModel.HEADER, version, sellable, pizzaSpec);
	}

	private static int nextVersion(int lastPdfVersion) {
		return lastPdfVersion < 1 ? 1 : lastPdfVersion + 1;
	}
}
