package org.example.sales;

import java.math.BigDecimal;

/**
 * Validated input for registering a sale and updating the product's inventory.
 * 
 * @param productId identifier of the product being sold
 * @param inventory current stock quantity of the product
 * @param discount discount applied to the sale
 * @param email email of the customer making the purchase
 * @param quantity units of product being sold
 */
public record RegisterSaleRequest(int productId, int quantity, int inventory, BigDecimal discount, String email) {

    /**
     * Validates and normalizes the supplied sale details.
     * 
     * @throws IllegalArgumentException if any value is invalid
     */
    public RegisterSaleRequest {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        if (inventory - quantity < 0) {
            throw new IllegalArgumentException("Inventory cannot be negative.");
        }

        if (discount == null) {
            discount = BigDecimal.ZERO;
        }

        // Matches the current PostgreSQL numeric(2,0) column.
        if (discount.signum() < 0
            || discount.compareTo(new BigDecimal("99")) > 0
            || discount.stripTrailingZeros().scale() > 0) {
        throw new IllegalArgumentException(
            "Discount must be a whole number between 0 and 99.");
        }

        if (email == null) {
            email = "";
        }

        email = email.strip();
    }
}
