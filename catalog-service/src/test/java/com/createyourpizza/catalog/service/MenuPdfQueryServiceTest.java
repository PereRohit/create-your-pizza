package com.createyourpizza.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.createyourpizza.catalog.domain.MenuPdf;
import com.createyourpizza.catalog.menu.InMemoryLatestMenuStore;
import com.createyourpizza.catalog.menu.LatestMenu;
import com.createyourpizza.catalog.repository.MenuPdfRepository;

@ExtendWith(MockitoExtension.class)
class MenuPdfQueryServiceTest {

	private static final Instant GENERATED = Instant.parse("2026-01-01T00:00:00Z");

	@Mock
	private MenuPdfRepository menuPdfRepository;

	private InMemoryLatestMenuStore latestMenuStore;
	private MenuPdfQueryService service;

	@BeforeEach
	void setUp() {
		latestMenuStore = new InMemoryLatestMenuStore();
		service = new MenuPdfQueryService(latestMenuStore, menuPdfRepository);
	}

	@Test
	void latestUsesRedisWithoutHittingDb() {
		byte[] pdf = bytes("redis-latest");
		latestMenuStore.put(new LatestMenu(pdf, 3, GENERATED));

		assertThat(service.getPdf(null)).isEqualTo(pdf);
		verifyNoInteractions(menuPdfRepository);
	}

	@Test
	void latestFallsBackToMaxVersionAndBackfillsRedis() {
		MenuPdf row = row(2, "db-latest");
		when(menuPdfRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(row));

		assertThat(service.getPdf(null)).isEqualTo(row.getPdf());
		assertThat(latestMenuStore.getLatest().pdf()).isEqualTo(row.getPdf());
		assertThat(latestMenuStore.getLatest().version()).isEqualTo(2);
		verify(menuPdfRepository, never()).findById(2);
	}

	@Test
	void latest404WhenRedisAndDbAreEmpty() {
		when(menuPdfRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPdf(null)).isInstanceOf(CatalogNotFoundException.class);
		assertThat(latestMenuStore.getLatest()).isNull();
	}

	@Test
	void requestedLatestVersionUsesRedis() {
		byte[] pdf = bytes("redis-v4");
		latestMenuStore.put(new LatestMenu(pdf, 4, GENERATED));

		assertThat(service.getPdf(4)).isEqualTo(pdf);
		verifyNoInteractions(menuPdfRepository);
	}

	@Test
	void historicalVersionReadsDbOnlyAndDoesNotWriteRedis() {
		latestMenuStore.put(new LatestMenu(bytes("redis-v5"), 5, GENERATED));
		MenuPdf historical = row(2, "db-v2");
		when(menuPdfRepository.findById(2)).thenReturn(Optional.of(historical));

		assertThat(service.getPdf(2)).isEqualTo(historical.getPdf());
		assertThat(latestMenuStore.getLatest().version()).isEqualTo(5);
		assertThat(latestMenuStore.getLatest().pdf()).isEqualTo(bytes("redis-v5"));
		verify(menuPdfRepository, never()).findTopByOrderByVersionDesc();
	}

	@Test
	void requestedLatestWhenRedisEmptyBackfillsFromDb() {
		MenuPdf latest = row(3, "db-v3");
		when(menuPdfRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(latest));

		assertThat(service.getPdf(3)).isEqualTo(latest.getPdf());
		assertThat(latestMenuStore.getLatest().version()).isEqualTo(3);
		assertThat(latestMenuStore.getLatest().pdf()).isEqualTo(latest.getPdf());
		verify(menuPdfRepository, never()).findById(3);
	}

	@Test
	void historicalWhenRedisEmptyReadsDbAndLeavesRedisEmpty() {
		when(menuPdfRepository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(row(4, "db-v4")));
		MenuPdf historical = row(1, "db-v1");
		when(menuPdfRepository.findById(1)).thenReturn(Optional.of(historical));

		assertThat(service.getPdf(1)).isEqualTo(historical.getPdf());
		assertThat(latestMenuStore.getLatest()).isNull();
	}

	@Test
	void unknownVersion404() {
		latestMenuStore.put(new LatestMenu(bytes("redis-v2"), 2, GENERATED));
		when(menuPdfRepository.findById(99)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPdf(99)).isInstanceOf(CatalogNotFoundException.class);
		assertThat(latestMenuStore.getLatest().version()).isEqualTo(2);
	}

	@Test
	void versionBelowOneIs404WithoutStoreAccess() {
		assertThatThrownBy(() -> service.getPdf(0)).isInstanceOf(CatalogNotFoundException.class);
		assertThatThrownBy(() -> service.getPdf(-3)).isInstanceOf(CatalogNotFoundException.class);
		assertThat(latestMenuStore.getLatest()).isNull();
		verifyNoInteractions(menuPdfRepository);
	}

	private static MenuPdf row(int version, String payload) {
		MenuPdf pdf = new MenuPdf();
		pdf.setVersion(version);
		pdf.setPdf(bytes(payload));
		pdf.setGeneratedAt(GENERATED);
		return pdf;
	}

	private static byte[] bytes(String payload) {
		return payload.getBytes();
	}
}
