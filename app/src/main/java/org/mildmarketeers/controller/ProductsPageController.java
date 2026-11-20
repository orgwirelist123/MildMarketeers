package org.mildmarketeers.controller;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import org.example.materials.AddMaterialRequest;
import org.example.materials.DeleteMaterialRequest;
import org.example.materials.TrimmedMaterialDetails;
import org.example.product.ProductDetails;
import org.example.sales.WholeSaleRequest;
import org.example.service.ProductsPageService;
import org.example.service.ProductsPageService.ProductInput;
import org.example.service.ProductsPageService.ProductSelector;
import org.example.service.ProductsPageService.SaleInput;
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
@RequestMapping("/api/productsPage")
public class ProductsPageController {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(ProductsPageController.class);

    private static final ProductsPageService service = new ProductsPageService();

    /**
     * Retrieves the current values to display in the editing form.
     */
    @GetMapping("/getProduct/{productId}")
    public ResponseEntity<ProductDetails> getProduct(
            @PathVariable("productId") int productId) throws SQLException {
        return service.getProduct(productId);
    }

    /**
     * Retrieves a list of all products.
     */
    @GetMapping("/listProduct")
    // @ExceptionHandler({SQLException.class, IllegalStateException.class})
    public ResponseEntity<ArrayList<ProductDetails>> displayProducts() throws SQLException {
        return service.displayProducts();
    }

    @GetMapping("/listAvailableParentProducts/{productId}")
    public ResponseEntity<ArrayList<ProductDetails>> displayParentProducts(
            @PathVariable("productId") int productId) throws SQLException {
        return service.displayParentProducts(productId);
    }

    /**
     * Adds a product using the existing request validation and repository.
     */
    @PostMapping("/addProduct")
    public ResponseEntity<Map<String, String>> addProduct(
            @RequestBody ProductInput input) throws SQLException {
        return service.addProduct(input);
    }

    /**
     * Saves edits and reports when the selected product no longer exists.
     */
    @PutMapping("/editProduct/{productId}")
    public ResponseEntity<Map<String, String>> editProduct(
            @PathVariable("productId") int productId,
            @RequestBody ProductInput input) throws SQLException {

        return service.editProduct(productId, input);
        // requireInventory(input);

        // EditProductRequest request
        //         = new EditProductRequest(
        //                 productId, input.name(), input.sellPrice(), input.inventory(), input.parentId());
        // if (!repository.update(request, input.tags(), input.tagColors())) {
        //     return ResponseEntity.status(404)
        //             .body(Map.of("message", "This product no longer exists."));
        // }
        // // Remove previous materials and add the new materials, if there are any
        // if (input.materials != null) {
        //     System.out.println(input.materials.size());
        //     materialRepository.deleteAllMaterials(productId);
        //     for (TrimmedMaterialDetails md : input.materials) {
        //         AddMaterialRequest mdRequest
        //                 = new AddMaterialRequest(md.materialName(), productId, md.materialCost(), "unit", new BigDecimal(0), new BigDecimal(0));
        //         materialRepository.addMaterial(mdRequest);
        //     }
        // }
        // return ResponseEntity.ok(
        //         Map.of("message", "Product updated successfully."));
    }

    /**
     * Query the database for products that contain the input
     */
    @PutMapping("/searchProduct")
    public ResponseEntity<Map<String, ArrayList<ProductDetails>>> queryProduct(@RequestBody String query) throws SQLException {
        // ArrayList<ProductDetails> products = repository.searchProducts(query);
        // return ResponseEntity.ok().body(Map.of("data", products));
        return service.queryProduct(query);
    }

    ;

  /** Archives the selected product. **/
  @PutMapping("/archiveProduct")
    public ResponseEntity<Map<String, String>> archiveProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        return service.archiveProduct(selector);
        // ArchiveProductRequest request
        //         = new ArchiveProductRequest(selector.productId);

        // if (!repository.archive(request)) {
        //     return ResponseEntity.status(404)
        //             .body(Map.of("message", "This product no longer exists."));
        // }
        // return ResponseEntity.ok(
        //         Map.of("message", "Product archived successfully."));
    }

    /**
     * Restores the selected product without changing stock or related records.
     */
    @PutMapping("/restoreProduct")
    public ResponseEntity<Map<String, String>> restoreProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        return service.restoreProduct(selector);
        // if (!repository.restore(new ArchiveProductRequest(selector.productId()))) {
        //     return ResponseEntity.status(404)
        //             .body(Map.of("message", "This product no longer exists."));
        // }
        // return ResponseEntity.ok(Map.of("message", "Product restored successfully."));
    }

    /**
     * Deletes the selected product. *
     */
    @PutMapping("/deleteProduct")
    public ResponseEntity<Map<String, String>> deleteProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        return service.deleteProduct(selector);
        // DeleteProductRequest request
        //         = new DeleteProductRequest(selector.productId);

        // if (!repository.delete(request)) {
        //     return ResponseEntity.status(404)
        //             .body(Map.of("message", "This product no longer exists."));
        // }
        // return ResponseEntity.ok(
        //         Map.of("message", "Product deleted successfully."));
    }

    /**
     * Deletes the selected product. *
     */
    @PutMapping("/duplicateProduct")
    public ResponseEntity<Map<String, String>> duplicateProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        return service.duplicateProduct(selector);
        // DuplicateProductRequest request
        //         = new DuplicateProductRequest(selector.productId);

        // if (!repository.duplicate(request)) {
        //     return ResponseEntity.status(404)
        //             .body(Map.of("message", "This product no longer exists."));
        // }
        // return ResponseEntity.ok(
        //         Map.of("message", "Product duplicated successfully."));
    }

    @PostMapping("/registerSale/{productId}")
    public ResponseEntity<Map<String, String>> registerSale(
            @PathVariable("productId") int productId,
            @RequestBody SaleInput input) throws SQLException {
        
        return service.registerSale(productId, input);

        // if (input == null || input.quantity() == null) {
        //     throw new IllegalArgumentException("Quantity is required.");
        // }

        // WholeSaleRequest request = new WholeSaleRequest(
        //         productId, input.quantity(), input.unitPrice(), input.discount(), input.email());

        // if (!repository.register(request)) {
        //     return ResponseEntity.status(404)
        //             .body(Map.of("message", "This product no longer exists."));
        // }

        // return ResponseEntity.status(201)
        //         .body(Map.of("message", "Sale registered successfully."));
    }

    /**
     * Adds a material. *
     */
    @PutMapping("/addMaterial")
    public ResponseEntity<Map<String, String>> addMaterial(
            @RequestBody MaterialInput input) throws SQLException {
        
        return service.addMaterial(input);
        // AddMaterialRequest request
        //         = new AddMaterialRequest(input.materialName(), input.productId(), input.materialCost(),
        //                 input.unitTerm(), input.unitQuantity(), input.usedQuantity());

        // if (!repository.addMaterial(request)) {
        //     throw new SQLException("The repository did not add the material.");
        // }

        // return ResponseEntity.status(201)
        //         .body(Map.of("message", "Material added successfully."));
    }

    /**
     * Deletes a material. *
     */
    @PutMapping("/deleteMaterial")
    public ResponseEntity<Map<String, String>> deleteMaterial(
            @RequestBody MaterialSelector selector) throws SQLException {

        return service.deleteMaterial(selector);

        // DeleteMaterialRequest request
        //         = new DeleteMaterialRequest(selector.materialName(), selector.productId());

        // if (!repository.deleteMaterial(request)) {
        //     throw new SQLException("The repository did not remove the material.");
        // }

        // return ResponseEntity.status(201)
        //         .body(Map.of("message", "Material deleted successfully."));
    }

    /**
     * Retrieve materials for a given product. *
     */
    @GetMapping("/listMaterial/{productId}")
    public ResponseEntity<ArrayList<TrimmedMaterialDetails>> retrieveMaterials(
            @PathVariable("productId") int productId) throws SQLException {
        return service.retrieveMaterials(productId);
        // ArrayList<TrimmedMaterialDetails> materials = repository.retrieveMaterials(productId);
        // return ResponseEntity.ok(materials);
    }

    /**
     * Returns validation errors so the form can explain what needs correcting.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    /**
     * Logs technical failures and returns a message suitable for the form.
     */
    @ExceptionHandler({SQLException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleStorageFailure(
            Exception exception) {
        LOGGER.error("Product operation failed.", exception);

        return ResponseEntity.status(500)
                .body(Map.of("message", "Could not complete the request. Please try again."));
    }

    // private static void requireInventory(ProductInput input) {
    //     if (input.inventory() == null) {
    //         throw new IllegalArgumentException("Inventory is required.");
    //     }
    // }
}
