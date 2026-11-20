package org.mildmarketeers.controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.materials.AddMaterialRequest;
import org.example.materials.DeleteMaterialRequest;
import org.example.materials.TrimmedMaterialDetails;
import org.example.product.ProductDetails;
import org.example.sales.SaleDetails;
import org.example.sales.WholeSaleRequest;
import org.example.service.SalesPageService;
import org.example.tags.TagRepository;
import org.example.service.ProductsPageService.MaterialInput;
import org.example.service.ProductsPageService.MaterialSelector;
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

@RestController
@RequestMapping("/api/salesPage")
public class SalesPageController {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(SalesPageController.class);

    private static final SalesPageService service = new SalesPageService();

    /** Returns saved sales, excluding sale history entries. */
    @GetMapping("/listSales")
    public ResponseEntity<List<SaleDetails>> listSales() throws SQLException {
        return service.listSales();
    }
}