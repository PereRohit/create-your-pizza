package com.createyourpizza.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.createyourpizza.catalog.domain.CatalogMeta;
import com.createyourpizza.catalog.lock.CatalogLockKeys;
import com.createyourpizza.catalog.lock.CatalogLockStore;
import com.createyourpizza.catalog.menu.InMemoryLatestMenuStore;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.pdf.MenuPdfRenderer;
import com.createyourpizza.catalog.repository.CatalogMetaRepository;
import com.createyourpizza.catalog.repository.MenuPdfRepository;
import com.createyourpizza.catalog.seed.CatalogMetaDefaults;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestPdfTriggerWebTest.TinyPdfRendererConfig.class)
class TestPdfTriggerWebTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CatalogLockStore lockStore;

	@Autowired
	private CatalogMetaRepository catalogMetaRepository;

	@Autowired
	private MenuPdfRepository menuPdfRepository;

	@Autowired
	private LatestMenuStore latestMenuStore;

	@BeforeEach
	void setUp() {
		lockStore.release(CatalogLockKeys.PDF_GENERATION);
		lockStore.release(CatalogLockKeys.CATALOG_WRITE);
		menuPdfRepository.deleteAll();
		catalogMetaRepository.deleteAll();
		if (latestMenuStore instanceof InMemoryLatestMenuStore memory) {
			memory.clear();
		}
		catalogMetaRepository.save(CatalogMetaDefaults.newSeedRow(Instant.parse("2026-01-01T00:00:00Z")));
	}

	@Test
	void postWithoutAuthRunsJobAndGeneratesWhenDirty() throws Exception {
		mockMvc.perform(post("/test/pdf/generate"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value(202))
				.andExpect(jsonPath("$.message").value("success"));

		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		assertThat(meta.isDirty()).isFalse();
		assertThat(meta.getLastPdfVersion()).isEqualTo(1);
		assertThat(menuPdfRepository.count()).isEqualTo(1);
		assertThat(latestMenuStore.get()).isPresent();
		assertThat(latestMenuStore.get().orElseThrow().version()).isEqualTo(1);
		assertThat(lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();
	}

	@Test
	void writeLockSkipsWithoutQueueingOrChangingDirty() throws Exception {
		lockStore.tryAcquire(CatalogLockKeys.CATALOG_WRITE, "writer", Duration.ofSeconds(30));

		mockMvc.perform(post("/test/pdf/generate"))
				.andExpect(status().isAccepted());

		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(menuPdfRepository.count()).isZero();
		assertThat(latestMenuStore.get()).isEmpty();
		assertThat(lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)).isFalse();
	}

	@Test
	void notDirtyIsNoOp() throws Exception {
		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		meta.setDirty(false);
		catalogMetaRepository.save(meta);

		mockMvc.perform(post("/test/pdf/generate"))
				.andExpect(status().isAccepted());

		CatalogMeta after = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		assertThat(after.isDirty()).isFalse();
		assertThat(after.getLastPdfVersion()).isZero();
		assertThat(menuPdfRepository.count()).isZero();
		assertThat(latestMenuStore.get()).isEmpty();
	}

	@Test
	void pdfLockHeldSkipsWithoutQueueing() throws Exception {
		lockStore.tryAcquire(CatalogLockKeys.PDF_GENERATION, "other-job", Duration.ofSeconds(120));

		mockMvc.perform(post("/test/pdf/generate"))
				.andExpect(status().isAccepted());

		CatalogMeta meta = catalogMetaRepository.findById(CatalogMetaDefaults.META_ID).orElseThrow();
		assertThat(meta.isDirty()).isTrue();
		assertThat(meta.getLastPdfVersion()).isZero();
		assertThat(menuPdfRepository.count()).isZero();
		assertThat(latestMenuStore.get()).isEmpty();
		assertThat(lockStore.isHeld(CatalogLockKeys.PDF_GENERATION)).isTrue();
	}

	@TestConfiguration
	static class TinyPdfRendererConfig {

		@Bean
		@Primary
		MenuPdfRenderer tinyMenuPdfRenderer() {
			return model -> "%PDF-1.4 test".getBytes();
		}
	}
}
