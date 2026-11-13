package org.example.product;

import java.math.BigDecimal;

/**
 * Current basic details of a stored product.
 *
 * @param productId product identifier
 * @param name product name
 * @param sellPrice selling price per unit
 * @param inventory current stock quantity
 * @param unitsSold current quantity of products sold
 * @param archived whether the product is archived
 * @param parentId product identifier if parent product, 0 if none
 */
public record ProductDetails(
    int productId, String name, BigDecimal sellPrice, int inventory,
    int unitsSold, boolean archived, int parentId) {}
