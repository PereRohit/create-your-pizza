package com.createyourpizza.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.createyourpizza.catalog.domain.MenuPdf;
import com.createyourpizza.catalog.menu.InMemoryLatestMenuStore;
import com.createyourpizza.catalog.menu.LatestMenu;
import com.createyourpizza.catalog.menu.LatestMenuStore;
import com.createyourpizza.catalog.repository.MenuPdfRepository;

@SpringBootTest
@AutoConfigureMockMvc
class MenuPdfControllerTest {

	private static final Instant GENERATED = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MenuPdfRepository menuPdfRepository;

	@Autowired
	private LatestMenuStore latestMenuStore;

	@BeforeEach
	void setUp() {
		menuPdfRepository.deleteAll();
		if (latestMenuStore instanceof InMemoryLatestMenuStore memory) {
			memory.clear();
		}
	}

	@Test
	void latestFromRedisIsRawPdfWithoutAuth() throws Exception {
		byte[] pdf = "%PDF-1.4 redis".getBytes();
		latestMenuStore.put(new LatestMenu(pdf, 2, GENERATED));

		mockMvc.perform(get("/api/menu.pdf"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(content().bytes(pdf));
	}

	@Test
	void latestFromDbBackfillsRedisAndReturnsRawPdf() throws Exception {
		menuPdfRepository.save(row(1, "%PDF-1.4 v1"));
		menuPdfRepository.save(row(3, "%PDF-1.4 v3"));

		mockMvc.perform(get("/api/menu.pdf"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(content().bytes("%PDF-1.4 v3".getBytes()));

		assertThat(latestMenuStore.get()).isPresent();
		assertThat(latestMenuStore.get().orElseThrow().version()).isEqualTo(3);
		assertThat(latestMenuStore.get().orElseThrow().pdf()).isEqualTo("%PDF-1.4 v3".getBytes());
	}

	@Test
	void historicalVersionIsRawPdfFromDb() throws Exception {
		menuPdfRepository.save(row(1, "%PDF-1.4 v1"));
		menuPdfRepository.save(row(2, "%PDF-1.4 v2"));
		latestMenuStore.put(new LatestMenu("%PDF-1.4 redis-v2".getBytes(), 2, GENERATED));

		mockMvc.perform(get("/api/menu.pdf").param("version", "1"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(content().bytes("%PDF-1.4 v1".getBytes()));
	}

	@Test
	void unknownVersionReturnsJson404Envelope() throws Exception {
		menuPdfRepository.save(row(1, "%PDF-1.4 v1"));

		mockMvc.perform(get("/api/menu.pdf").param("version", "9").accept(MediaType.APPLICATION_PDF))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Menu PDF not found"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	void noRowsReturnsJson404Envelope() throws Exception {
		mockMvc.perform(get("/api/menu.pdf").accept(MediaType.APPLICATION_PDF))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Menu PDF not found"));
	}

	private static MenuPdf row(int version, String payload) {
		MenuPdf pdf = new MenuPdf();
		pdf.setVersion(version);
		pdf.setPdf(payload.getBytes());
		pdf.setGeneratedAt(GENERATED);
		return pdf;
	}
}
