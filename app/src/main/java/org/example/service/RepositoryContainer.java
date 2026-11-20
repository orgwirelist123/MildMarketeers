package org.example.service;

import org.example.materials.MaterialRepository;
import org.example.product.ProductRepository;
import org.example.sales.SaleRepository;

public class RepositoryContainer {
    private final ProductRepository productRepository = new ProductRepository();
    private final MaterialRepository materialRepository = new MaterialRepository();
    private final SaleRepository saleRepository = new SaleRepository();
    
}
