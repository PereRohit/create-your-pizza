package com.createyourpizza.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductCategoryConverter implements AttributeConverter<ProductCategory, String> {

	@Override
	public String convertToDatabaseColumn(ProductCategory attribute) {
		return attribute == null ? null : attribute.getDbValue();
	}

	@Override
	public ProductCategory convertToEntityAttribute(String dbData) {
		return dbData == null ? null : ProductCategory.fromDbValue(dbData);
	}
}
