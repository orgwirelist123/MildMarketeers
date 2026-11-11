package org.example.sales;

import java.math.BigDecimal;

/** Validated sale input with one dollar discount applied to the whole sale. */
public record WholeSaleRequest(
    int productId,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal discount,
    String email) {

  private static final BigDecimal MAX_MONEY =
      new BigDecimal("9999999999.99");

  public WholeSaleRequest {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Quantity must be greater than zero.");
    }

    unitPrice = validateMoney(unitPrice, "Unit price");
    discount = validateMoney(discount, "Discount");

    BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    if (discount.compareTo(subtotal) > 0) {
      throw new IllegalArgumentException("Discount cannot exceed the sale subtotal.");
    }

    email = email == null ? "" : email.strip();
    if (email.length() > 256) {
      throw new IllegalArgumentException("Email cannot exceed 256 characters.");
    }
  }

  /** Calculates the final total without rounding a discount into unit prices. */
  public BigDecimal total() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity)).subtract(discount);
  }

  private static BigDecimal validateMoney(BigDecimal value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " is required.");
    }

    if (value.signum() < 0
        || value.compareTo(MAX_MONEY) > 0
        || value.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException(
          field + " must be between 0 and 9999999999.99 with at most two decimal places.");
    }

    return value.setScale(2);
  }
}
