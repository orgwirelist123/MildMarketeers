package org.example.sales;

import java.time.LocalDate;
import java.time.LocalTime;

/** Stored sale information available for the sales page. */
public record SaleDetails(
    int saleId,
    int productId,
    String productName,
    LocalDate date,
    LocalTime time) {}
