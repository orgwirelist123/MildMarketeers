package org.mildmarketeers.controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import org.example.tags.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connects product forms to the product repository. */
@RestController
@RequestMapping("/api/tags")
public class TagController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProductController.class);

  private final TagRepository repository = new TagRepository();

  @GetMapping("/{productId}")
  public ResponseEntity<ArrayList<String>> displayTags(
    @PathVariable("productId") int productId) throws SQLException{
          ArrayList<String> productTags = repository.findTagsByProductId(productId);
          return ResponseEntity.ok(productTags);
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
}