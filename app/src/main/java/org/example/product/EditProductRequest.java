package org.example.product;

import java.math.BigDecimal;

/**
 * Validated input for editing a product's basic details.
 *
 * @param productId identifier of the existing product
 * @param name updated product name
 * @param sellPrice updated selling price
 * @param inventory updated stock quantity
 */
public record EditProductRequest(
    int productId, String name, BigDecimal sellPrice, int inventory) {

  /**
   * Validates and normalizes the supplied product details.
   *
   * @throws IllegalArgumentException if any editable value is invalid
   */
  public EditProductRequest {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Product name is required.");
    }

    name = name.strip();

    if (name.codePointCount(0, name.length()) > 50) {
      throw new IllegalArgumentException(
          "Product name must contain no more than 50 characters.");
    }

    if (sellPrice == null) {
      throw new IllegalArgumentException("Selling price is required.");
    }

    // Matches the current PostgreSQL numeric(2,0) column.
    if (sellPrice.signum() < 0
        || sellPrice.compareTo(new BigDecimal("99")) > 0
        || sellPrice.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException(
          "Selling price must be a whole number between 0 and 99.");
    }

    if (inventory < 0) {
      throw new IllegalArgumentException("Inventory cannot be negative.");
    }
  }
}
