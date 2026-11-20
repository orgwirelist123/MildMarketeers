package org.example.materials;

/**
 * Validated input for removing a material from the 'Material' table.
 *
 * @param materialName material name
 * @param productId product identifier
 * 
 */

public record DeleteMaterialRequest(
	String materialName, int productId) {
	
	/**
	 * Validates and normalizes the supplied material details.
	 *
	 * @throws IllegalArgumentException if any editable value is invalid
	 */
	public DeleteMaterialRequest {
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
	}
}
