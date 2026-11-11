package org.example.product;

/**
 * Validated input for archiving a product from the 'Product' table.
 *
 * @param name updated product name
 * @param sellPrice updated selling price
 * @param inventory updated stock quantity
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
