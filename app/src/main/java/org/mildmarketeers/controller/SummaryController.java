package org.mildmarketeers.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provides date-filtered summary totals without counting history copies. */
@RestController
@RequestMapping("/api/summary")
public class SummaryController {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(SummaryController.class);

  /** Reads one consistent set of sales for totals, charts, and rankings. */
  @GetMapping
  public Map<String, Object> summary(
      @RequestParam("start") String start,
      @RequestParam("end") String end) throws SQLException {
    LocalDate first;
    LocalDate last;
    try {
      first = LocalDate.parse(start);
      last = LocalDate.parse(end);
    } catch (java.time.format.DateTimeParseException exception) {
      throw new IllegalArgumentException("Choose valid start and end dates.");
    }

    if (last.isBefore(first) || ChronoUnit.DAYS.between(first, last) > 365) {
      throw new IllegalArgumentException(
          "Choose a date range of no more than 366 days.");
    }

    Map<LocalDate, Totals> days = new LinkedHashMap<>();
    for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
      days.put(day, new Totals());
    }
    Map<Integer, Totals> products = new LinkedHashMap<>();
    Map<Integer, String> names = new LinkedHashMap<>();
    Totals totals = new Totals();

    String sql = """
        SELECT s.product_id, p.name, s.date, s.quantity,
               s.quantity * s.unit_price - s.discount AS revenue,
               s.production_cost_per_unit
        FROM ONLY public."Sale" s
        LEFT JOIN ONLY public."Product" p ON p.product_id = s.product_id
        WHERE s.date BETWEEN ? AND ?
        ORDER BY s.date, s.time, s.sale_id
        """;

    try (var connection = DatabaseConnection.open();
        var statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(10);
      statement.setObject(1, first);
      statement.setObject(2, last);
      try (var rows = statement.executeQuery()) {
        while (rows.next()) {
          int productId = rows.getInt("product_id");
          int quantity = rows.getInt("quantity");
          LocalDate day = rows.getObject("date", LocalDate.class);
          BigDecimal revenue = rows.getBigDecimal("revenue");
          BigDecimal cost = rows.getBigDecimal("production_cost_per_unit");
          String name = rows.getString("name");
          names.put(productId, name == null ? "Unavailable product" : name);
          totals.add(quantity, revenue, cost);
          days.get(day).add(quantity, revenue, cost);
          products.computeIfAbsent(productId, key -> new Totals())
              .add(quantity, revenue, cost);
        }
      }
    }

    List<Map<String, Object>> daily = new ArrayList<>();
    days.forEach((date, values) -> {
      Map<String, Object> row = values.result();
      row.put("date", date.toString());
      daily.add(row);
    });

    List<Map<String, Object>> productRows = new ArrayList<>();
    products.forEach((id, values) -> {
      Map<String, Object> row = values.result();
      row.put("productId", id);
      row.put("name", names.get(id));
      productRows.add(row);
    });

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("start", first.toString());
    response.put("end", last.toString());
    response.put("totals", totals.result());
    response.put("daily", daily);
    response.put("products", productRows);
    return response;
  }

  /** Returns readable validation failures to the date picker. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> invalidRequest(
      IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", exception.getMessage()));
  }

  /** Logs database failures without exposing connection details. */
  @ExceptionHandler({SQLException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> storageFailure(Exception exception) {
    LOGGER.error("Could not load summary.", exception);
    return ResponseEntity.status(500)
        .body(Map.of("message", "Could not load summary. Please try again."));
  }

  /** Accumulates money exactly; unknown costs never become zero costs. */
  private static final class Totals {
    private long units;
    private long transactions;
    private long missingCostSales;
    private BigDecimal revenue = BigDecimal.ZERO;
    private BigDecimal profit = BigDecimal.ZERO;

    void add(int quantity, BigDecimal amount, BigDecimal unitCost) {
      units += quantity;
      transactions++;
      revenue = revenue.add(amount);
      if (unitCost == null) {
        missingCostSales++;
      } else {
        profit = profit.add(
            amount.subtract(unitCost.multiply(BigDecimal.valueOf(quantity))));
      }
    }

    Map<String, Object> result() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("units", units);
      result.put("transactions", transactions);
      result.put("revenue", revenue.toPlainString());
      result.put("profit", missingCostSales == 0 ? profit.toPlainString() : null);
      result.put("missingCostSales", missingCostSales);
      return result;
    }
  }
}
