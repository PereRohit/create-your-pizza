package com.createyourpizza.catalog.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.createyourpizza.catalog.domain.MenuPdf;
import com.createyourpizza.catalog.menu.LatestMenu;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.repository.MenuPdfRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MenuPdfQueryService {

	private static final String NOT_FOUND = "Menu PDF not found";

	private final LatestMenuStore latestMenuStore;
	private final MenuPdfRepository menuPdfRepository;

	public byte[] getPdf(Integer version) {
		if (version != null && version < 1) {
			throw new CatalogNotFoundException(NOT_FOUND);
		}
		if (version == null) {
			return latest();
		}
		return version(version);
	}

	private byte[] latest() {
		return latestMenuStore.get()
				.map(menu -> menu.pdf())
				.orElseGet(this::latestFromDbAndBackfill);
	}

	private byte[] latestFromDbAndBackfill() {
		MenuPdf row = requireLatestRow();
		latestMenuStore.put(toLatest(row));
		return row.getPdf();
	}

	private byte[] version(int version) {
		Optional<LatestMenu> cached = latestMenuStore.get();
		if (cached.isPresent()) {
			if (cached.get().version() == version) {
				return cached.get().pdf();
			}
			return historicalFromDb(version);
		}
		MenuPdf latestRow = requireLatestRow();
		if (latestRow.getVersion() == version) {
			latestMenuStore.put(toLatest(latestRow));
			return latestRow.getPdf();
		}
		return historicalFromDb(version);
	}

	private byte[] historicalFromDb(int version) {
		return menuPdfRepository.findById(version)
				.orElseThrow(() -> new CatalogNotFoundException(NOT_FOUND))
				.getPdf();
	}

	private MenuPdf requireLatestRow() {
		return menuPdfRepository.findTopByOrderByVersionDesc()
				.orElseThrow(() -> new CatalogNotFoundException(NOT_FOUND));
	}

	private static LatestMenu toLatest(MenuPdf row) {
		return new LatestMenu(row.getPdf(), row.getVersion(), row.getGeneratedAt());
	}
}
