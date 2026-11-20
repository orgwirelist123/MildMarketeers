package org.example.materials;

import java.math.BigDecimal;

/**
 * Validated input for editing a material to the 'Material' table.
 *
 * @param materialName material name
 * @param productId product identifier
 * @param materialCost stock quantity
 * @param unitTerm parent product id.
 * @param unitQuantity unit quantity
 * @param usedQuantity used quantity
 * 
 */
public record EditMaterialRequest(
	String materialName, int productId, BigDecimal materialCost,
	String unitTerm, BigDecimal unitQuantity, BigDecimal usedQuantity) {

	/**
	 * Validates and normalizes the supplied material details.
	 *
	 * @throws IllegalArgumentException if any editable value is invalid
	 */
	public EditMaterialRequest {
		if (materialName == null || materialName.isBlank()) {
			throw new IllegalArgumentException("Material name is required.");
		}

		materialName = materialName.strip();

		if (materialName.codePointCount(0, materialName.length()) > 50) {
			throw new IllegalArgumentException(
				"Material name must contain no more than 50 characters.");
		}

		if (productId < 0) {
			throw new IllegalArgumentException("Product id cannot be negative.");
		}

		if (materialCost == null) {
			throw new IllegalArgumentException("Enter a cost for the component \"" + materialName + "\".");
		}

		// Matches the current PostgreSQL numeric(12,2) column.
		if (materialCost.signum() < 0
			|| materialCost.compareTo(new BigDecimal("9999999999.99")) > 0
			|| materialCost.stripTrailingZeros().scale() > 2) {
			throw new IllegalArgumentException(
				"Component cost must be between 0 and 9999999999.99 "
					+ "with at most two decimal places.");
		}

		
		if (unitTerm == null || unitTerm.isBlank()) {
			throw new IllegalArgumentException("Material unit term is required.");
		}

		unitTerm = unitTerm.strip();

		if (unitTerm.codePointCount(0, unitTerm.length()) > 50) {
			throw new IllegalArgumentException(
				"Material name must contain no more than 50 characters.");
		}

		if (unitQuantity.signum() < 0) {
			throw new IllegalArgumentException("Unit quantity cannot be negative.");
		}

		if (usedQuantity.signum() < 0) {
			throw new IllegalArgumentException("Used quantity cannot be negative.");
		}
	}
}
