package com.createyourpizza.catalog.pdf;

/**
 * PDF bytes port (Build plan §5). Tests supply a fake that records the model.
 */
public interface MenuPdfRenderer {

	byte[] render(MenuPdfModel model);
}
