package org.mildmarketeers.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

import org.example.sales.RegisterSaleRequest;
import org.example.sales.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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

    public record SaleInput(Integer quantity, Integer inventory, Integer unitsSold, BigDecimal sellPrice) {}

    @PutMapping("/{productId}")
    public ResponseEntity<Map<String, String>> registerSale(
        @PathVariable("productId") int productId,
        @RequestBody SaleInput input) throws SQLException {

        RegisterSaleRequest request = 
            new RegisterSaleRequest(
                productId, input.quantity(), input.inventory(), input.unitsSold(), null, null);
            
        return ResponseEntity.ok(
            Map.of("message", "Register sale function successfully called."));
    }
    
}
