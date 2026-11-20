package org.example.materials;

import java.math.BigDecimal;

/**
 * Current basic details of a stored product.
 *
 * @param materialName material name
 * @param materialCost cost of the material per unit
 */
public record TrimmedMaterialDetails(
	String materialName, BigDecimal materialCost) {}
