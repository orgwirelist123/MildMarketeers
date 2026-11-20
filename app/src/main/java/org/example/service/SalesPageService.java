package org.example.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.materials.AddMaterialRequest;
import org.example.materials.DeleteMaterialRequest;
import org.example.materials.MaterialRepository;
import org.example.materials.TrimmedMaterialDetails;
import org.example.product.AddProductRequest;
import org.example.product.ArchiveProductRequest;
import org.example.product.DeleteProductRequest;
import org.example.product.DuplicateProductRequest;
import org.example.product.EditProductRequest;
import org.example.product.ProductDetails;
import org.example.product.ProductRepository;
import org.example.sales.SaleRepository;
import org.example.sales.WholeSaleRepository;
import org.example.sales.WholeSaleRequest;
import org.example.sales.SaleDetails;
import org.example.sales.SaleQueryRepository;
import org.example.tags.TagRepository;
import org.mildmarketeers.controller.ProductController;
import org.mildmarketeers.controller.ProductController.ProductInput;
import org.mildmarketeers.controller.ProductController.ProductSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

public class SalesPageService {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(ProductsPageService.class);

    private final WholeSaleRepository wholeSaleRepository = new WholeSaleRepository();
    private final SaleQueryRepository saleQueryRepository = new SaleQueryRepository();

    
    /** Returns saved sales, excluding sale history entries. */
    @GetMapping("/listSales")
    public ResponseEntity<List<SaleDetails>> listSales() throws SQLException {
        return ResponseEntity.ok(saleQueryRepository.findAll());
    }
}