package org.mildmarketeers.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import org.example.sales.WholeSaleRepository;
import org.example.sales.WholeSaleRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registers a sale and updates its product in one transaction. */
@RestController
@RequestMapping("/api/sales")
public class SaleController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SaleController.class);

  private final WholeSaleRepository repository = new WholeSaleRepository();

  public record SaleInput(
      Integer quantity, BigDecimal unitPrice, BigDecimal discount, String email) {}

  @PostMapping("/{productId}")
  public ResponseEntity<Map<String, String>> registerSale(
      @PathVariable("productId") int productId,
      @RequestBody SaleInput input) throws SQLException {

    if (input == null || input.quantity() == null) {
      throw new IllegalArgumentException("Quantity is required.");
    }

    WholeSaleRequest request = new WholeSaleRequest(
        productId, input.quantity(), input.unitPrice(), input.discount(), input.email());

    if (!repository.register(request)) {
      return ResponseEntity.status(404)
          .body(Map.of("message", "This product no longer exists."));
    }

    return ResponseEntity.status(201)
        .body(Map.of("message", "Sale registered successfully."));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleInvalidInput(
      IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler({SQLException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> handleStorageFailure(
      Exception exception) {
    LOGGER.error("Sale registration failed.", exception);
    return ResponseEntity.status(500)
        .body(Map.of("message", "Could not register the sale. Please check the sales list before retrying."));
  }
}
