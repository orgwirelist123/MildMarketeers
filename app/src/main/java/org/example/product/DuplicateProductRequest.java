package org.example.product;

/**
 * Validated input for deleting a product from the 'Product' and 'ProductHistory' table.
 *
 * @param name updated product name
 * @param sellPrice updated selling price
 * @param inventory updated stock quantity
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
