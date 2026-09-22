package com.createyourpizza.catalog.pdf;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Basic OpenPDF menu (Spec FR-16a): header, vN, sellable rows, pizza-spec in own space.
 */
public class OpenPdfMenuPdfRenderer implements MenuPdfRenderer {

	@Override
	public byte[] render(MenuPdfModel model) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Document document = new Document();
			PdfWriter.getInstance(document, out);
			document.open();
			document.add(new Paragraph(model.header()));
			document.add(new Paragraph("v" + model.version()));
			for (MenuPdfModel.SellableRow row : model.sellable()) {
				String line = row.name() + " " + price(row.price());
				if (row.optionsAvailable()) {
					line = line + " " + MenuPdfModel.OPTIONS_AVAILABLE;
				}
				document.add(new Paragraph(line));
			}
			document.add(new Paragraph("Pizza spec"));
			for (MenuPdfModel.OptionRow option : model.pizzaSpec()) {
				document.add(new Paragraph(option.name() + " " + price(option.price())));
			}
			document.close();
			return out.toByteArray();
		}
		catch (DocumentException ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to render menu PDF", ex));
		}
	}

	private static String price(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
