package org.example.product;

/**
 * Validated input for archiving a product from the 'Product' table.
 *
 * @param productId identifier of the existing product
 */
public record ArchiveProductRequest(
		int productId) {

	/**
	 * Validates and normalizes the supplied product details.
	 *
	 * @throws IllegalArgumentException if any editable value is invalid
	 */
	public ArchiveProductRequest {}
}
