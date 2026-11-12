package org.example.product;

import java.math.BigDecimal;

/**
 * Validated input for editing a product's basic details.
 *
 * @param productId identifier of the existing product
 * @param name updated product name
 * @param sellPrice updated selling price
 * @param inventory updated stock quantity
 * @param parentId updated parent product id
 *    0 signifies no parent product, which is inserted as 'null' into the SQL database
 */
public record EditProductRequest(
    int productId, String name, BigDecimal sellPrice, int inventory, int parentId) {

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

    // Matches the current PostgreSQL numeric(12,2) column.
    if (sellPrice.signum() < 0
        || sellPrice.compareTo(new BigDecimal("9999999999.99")) > 0
        || sellPrice.stripTrailingZeros().scale() > 2) {
        throw new IllegalArgumentException(
          "Selling price must be between 0 and 9999999999.99 "
              + "with at most two decimal places.");
    }

    if (inventory < 0) {
      throw new IllegalArgumentException("Inventory cannot be negative.");
    }

    if (parentId < 0) {
      throw new IllegalArgumentException("Parent product id cannot be less than 0.");
    }
  }
}
