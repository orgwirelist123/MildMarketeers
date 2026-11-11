package org.example.sales;

import java.math.BigDecimal;

/**
 * Validated input for registering a sale and updating the product's inventory.
 * 
 * @param productId identifier of the product being sold
 * @param soldQuantity units of product being sold
 * @param inventory current stock quantity of the product
 * @param originalUnitPrice price per unit without discount
 * @param sellPrice price per unit being sold at with discount
 * @param totalPrice total price of sale being made with discount
 * @param pastUnitsSold units of product already sold
 * @param email email of the customer making the purchase
 */
public record RegisterSaleRequest(int productId, int soldQuantity, int inventory, BigDecimal originalUnitPrice, 
    BigDecimal sellPrice, BigDecimal totalPrice, int pastUnitsSold, String email) {
    /**
     * Validates and normalizes the supplied sale details.
     * 
     * @throws IllegalArgumentException if any value is invalid
     */
    public RegisterSaleRequest {
        if (soldQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        if (inventory - soldQuantity < 0) {
            throw new IllegalArgumentException("Inventory cannot be negative.");
        }

        if (sellPrice == null) {
            throw new IllegalArgumentException("Selling price is required.");
        }

        if (email == null) {
            email = "";
        }

        email = email.strip();
    }
}
