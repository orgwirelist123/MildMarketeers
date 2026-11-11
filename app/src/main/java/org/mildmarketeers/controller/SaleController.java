package org.mildmarketeers.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

import org.example.sales.RegisterSaleRequest;
import org.example.sales.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SaleController {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(SaleController.class);

    private final SaleRepository repository = new SaleRepository();

    public record SaleInput(Integer soldQuantity, Integer inventory, BigDecimal originalUnitPrice,
        BigDecimal sellPrice, BigDecimal totalPrice, int pastUnitsSold, String email) {}

    /** Adds a sale using the existing request validation and repository. */
    @PostMapping("/{productId}")
    public ResponseEntity<Map<String, String>> registerSale(
        @PathVariable("productId") int productId,
        @RequestBody SaleInput input) throws SQLException {
        requireQuantity(input);

        RegisterSaleRequest request = 
            new RegisterSaleRequest(
                productId, input.soldQuantity(), input.inventory(), input.originalUnitPrice(), input.sellPrice(), 
                input.totalPrice(), input.pastUnitsSold(), input.email());

        if(!repository.registerSale(request)) {
            throw new SQLException("The repository did not add the sale.");
        }
            
        return ResponseEntity.ok(
            Map.of("message", "Register sale function successfully called."));
    }

    /** Saves updates for product and reports when the selected product no longer exists. */
    @PutMapping("/update/{productId}")
    public ResponseEntity<Map<String, String>> updateProduct(
        @PathVariable("productId") int productId,
        @RequestBody SaleInput input) throws SQLException {
        requireQuantity(input);

        RegisterSaleRequest request = 
            new RegisterSaleRequest(
                productId, input.soldQuantity(), input.inventory(), input.originalUnitPrice(), input.sellPrice(), 
                input.totalPrice(), input.pastUnitsSold(), input.email());

        if (!repository.updateProduct(request)) {
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

    /** Checks there is a value inputted for quantity field. */
    private static void requireQuantity(SaleInput input) {
        if (input.soldQuantity() == null) {
            throw new IllegalArgumentException("Quantity is required.");
        }
    }
}
