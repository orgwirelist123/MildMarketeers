package org.example.materials;

import java.math.BigDecimal;

/**
 * Current basic details of a stored product.
 *
 * @param materialName material name
 * @param productId product identifier that material is attached to
 * @param materialCost cost of the material per unit
 * @param unitTerm term of the unit
 * @param unitQuantity quantity of the unit
 * @param usedQuantity quantity used of the unit
 */
public record MaterialDetails(
    String materialName, int productId, BigDecimal materialCost, String unitTerm,
    BigDecimal unitQuantity, BigDecimal usedQuantity) {}
