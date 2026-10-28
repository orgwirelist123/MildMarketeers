package org.example.product;

import java.math.BigDecimal;

/**
 * Current basic details of a stored product.
 *
 * @param productId product identifier
 * @param name product name
 * @param sellPrice selling price per unit
 * @param inventory current stock quantity
 */
public record ProductDetails(
    int productId, String name, BigDecimal sellPrice, int inventory) {}
