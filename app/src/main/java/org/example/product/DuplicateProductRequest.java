package org.example.product;

/**
 * Validated input for deleting a product from the 'Product' and 'ProductHistory' table.
 *
 * @param productId identifier of the existing product
 */
public record DuplicateProductRequest(
    int productId) {

  /**
   * Validates and normalizes the supplied product details.
   *
   * @throws IllegalArgumentException if any editable value is invalid
   */
  public DuplicateProductRequest {}
}
