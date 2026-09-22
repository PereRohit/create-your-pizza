package com.createyourpizza.catalog.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

class OpenPdfMenuPdfRendererTest {

	@Test
	void rendersPdfWithLockedStrings() throws IOException {
		MenuPdfModel model = new MenuPdfModel(
				MenuPdfModel.HEADER,
				1,
				List.of(
						new MenuPdfModel.SellableRow("Garlic Bread", new BigDecimal("80.00"), false),
						new MenuPdfModel.SellableRow("Margherita", new BigDecimal("299.00"), true)),
				List.of(new MenuPdfModel.OptionRow("olive", new BigDecimal("15.00"))));

		byte[] bytes = new OpenPdfMenuPdfRenderer().render(model);

		assertThat(new String(bytes, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
		String text = extractText(bytes);
		assertThat(text).contains("Create Your Pizza");
		assertThat(text).contains("v1");
		assertThat(text).contains("Garlic Bread");
		assertThat(text).contains("80");
		assertThat(text).contains("Margherita");
		assertThat(text).contains("options available");
		assertThat(text).contains("Pizza spec");
		assertThat(text).contains("olive");
		assertThat(text).contains("15");
	}

	private static String extractText(byte[] bytes) throws IOException {
		try (PdfReader reader = new PdfReader(bytes)) {
			return new PdfTextExtractor(reader).getTextFromPage(1);
		}
	}
}
