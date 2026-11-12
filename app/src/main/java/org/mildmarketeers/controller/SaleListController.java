package org.mildmarketeers.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.example.sales.SaleDetails;
import org.example.sales.SaleQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides saved sales for the sales page. */
@RestController
@RequestMapping("/api/sales")
public class SaleListController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SaleListController.class);

  private final SaleQueryRepository repository = new SaleQueryRepository();

  /** Returns saved sales, excluding sale history entries. */
  @GetMapping("/list")
  public ResponseEntity<List<SaleDetails>> listSales() throws SQLException {
    return ResponseEntity.ok(repository.findAll());
  }

  /** Logs storage failures and returns a message suitable for the page. */
  @ExceptionHandler({SQLException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> handleStorageFailure(
      Exception exception) {
    LOGGER.error("Could not load sales.", exception);

    return ResponseEntity.status(500)
        .body(Map.of("message", "Could not load sales. Please try again."));
  }
}
