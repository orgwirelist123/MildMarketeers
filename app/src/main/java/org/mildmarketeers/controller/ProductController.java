package org.mildmarketeers.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.ArrayList;
import org.example.product.AddProductRequest;
import org.example.product.EditProductRequest;
import org.example.product.ProductDetails;
import org.example.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

/** Connects product forms to the product repository. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProductController.class);

  private final ProductRepository repository = new ProductRepository();

  /** Input shared by the Add Product and Edit Product forms. */
  public record ProductInput(String name, BigDecimal sellPrice, Integer inventory) {}

  /** Retrieves the current values to display in the editing form. */
  @GetMapping("/{productId}")
  public ResponseEntity<ProductDetails> getProduct(
      @PathVariable("productId") int productId) throws SQLException {
    return repository.findById(productId)
        .map(product -> ResponseEntity.ok(product))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Adds a product using the existing request validation and repository. */
  @PostMapping
  public ResponseEntity<Map<String, String>> addProduct(
      @RequestBody ProductInput input) throws SQLException {
    requireInventory(input);

    AddProductRequest request =
        new AddProductRequest(input.name(), input.sellPrice(), input.inventory());

    if (!repository.update(request)) {
      throw new SQLException("The repository did not add the product.");
    }

    return ResponseEntity.status(201)
        .body(Map.of("message", "Product added successfully."));
  }

  /** Saves edits and reports when the selected product no longer exists. */
  @PutMapping("/{productId}")
  public ResponseEntity<Map<String, String>> editProduct(
      @PathVariable("productId") int productId,
      @RequestBody ProductInput input) throws SQLException {
    requireInventory(input);

    EditProductRequest request =
        new EditProductRequest(
            productId, input.name(), input.sellPrice(), input.inventory());

    if (!repository.update(request)) {
      return ResponseEntity.status(404)
          .body(Map.of("message", "This product no longer exists."));
    }

    return ResponseEntity.ok(
        Map.of("message", "Product updated successfully."));
  }

  /** Returns validation errors so the form can explain what needs correcting. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleInvalidInput(
      IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", exception.getMessage()));
  }

  /** Logs technical failures and returns a message suitable for the form. */
  @ExceptionHandler({SQLException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> handleStorageFailure(
      Exception exception) {
    LOGGER.error("Product operation failed.", exception);

    return ResponseEntity.status(500)
        .body(Map.of("message", "Could not complete the request. Please try again."));
  }

  /**  */
  @GetMapping ("/display")
  @ExceptionHandler({SQLException.class, IllegalStateException.class})
  public ResponseEntity<String> diplayProducts() throws SQLException{
    ArrayList<ProductDetails> products = repository.retrieveProducts();
    ObjectMapper objMapper = new ObjectMapper();
    String jsonList = objMapper.writeValueAsString(products);
    return ResponseEntity.ok(jsonList);
  }

  private static void requireInventory(ProductInput input) {
    if (input.inventory() == null) {
      throw new IllegalArgumentException("Inventory is required.");
    }
  }
}
