package org.example.product;

import java.math.BigDecimal;

/**
 * Current basic details of a stored product.
 *
 * @param productId product identifier
 * @param name product name
 * @param sellPrice selling price per unit
 * @param inventory current stock quantity
 * @param archived if product is archived
 */
public record ProductDetails(
    int productId, String name, BigDecimal sellPrice, int inventory, boolean archived) {}
