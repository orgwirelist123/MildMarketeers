package org.example.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
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

public class ProductsPageService {

    private static final Logger LOGGER
            = LoggerFactory.getLogger(ProductsPageService.class);

    private final ProductRepository productRepository = new ProductRepository();
    private final MaterialRepository materialRepository = new MaterialRepository();
    private final WholeSaleRepository saleRepository = new WholeSaleRepository();
    private final TagRepository tagRepository = new TagRepository();

    /**
     * Input shared by the Add Product and Edit Product forms.
     */
    public record ProductInput(String name, BigDecimal sellPrice, Integer inventory, Integer parentId, ArrayList<TrimmedMaterialDetails> materials, java.util.List<String> tags, java.util.Map<String, String> tagColors) {

        public ProductInput(String name, BigDecimal sellPrice, Integer inventory,
                Integer parentId, ArrayList<TrimmedMaterialDetails> materials) {
            this(name, sellPrice, inventory, parentId, materials, null, null);
        }

        public ProductInput(String name, BigDecimal sellPrice, Integer inventory,
                Integer parentId, ArrayList<TrimmedMaterialDetails> materials, java.util.List<String> tags) {
            this(name, sellPrice, inventory, parentId, materials, tags, null);
        }
    }

    /**
     * Selector shared by the Delete Product, Archive Product, and Duplicate
     * Product forms.
     */
    public record ProductSelector(int productId) {}

    public record SaleInput(
            Integer quantity, BigDecimal unitPrice, BigDecimal discount, String email) {}

    /**
     * TO DO: - Add a product with materials Retrieves the product details of
     * all the products in the database.
     *
     * @param request validated material data
     * @return true if the material was added, false otherwise
     * @throws SQLException if the database query fails
     */
    public boolean addProductWithMaterials() {

        return true;
    }

    /**
     *  /{productId} -> getProduct /list -> displayProducts
     * /listAvailableParents/{productId} -> displayParentProducts ??? ->
     * addProduct /{productId} -> editProduct /search -> queryProduct /archive
     * -> archiveProduct /restore -> restoreProduct /delete -> deleteProduct
     * /duplicate -> duplicateProduct
     *
     *      *handleInvalidInput handleStorageFailure
     *
     *      *private requireInventory
     *
     *      *ProductsPageService -> Products, Materials, Tags, Sales
     * SalesPageService -> Sales DatabasePageService -> Database
     */
    /**
     * Retrieves the current values to display in the editing form.
     */
    public ResponseEntity<ProductDetails> getProduct(
            int productId) throws SQLException {
        return productRepository.findById(productId)
                .map(product -> ResponseEntity.ok(product))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Retrieves a list of all products.
     */
    @GetMapping("/list")
    // @ExceptionHandler({SQLException.class, IllegalStateException.class})
    public ResponseEntity<ArrayList<ProductDetails>> displayProducts() throws SQLException {
        ArrayList<ProductDetails> products = productRepository.retrieveProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/listAvailableParents/{productId}")
    public ResponseEntity<ArrayList<ProductDetails>> displayParentProducts(
            @PathVariable("productId") int productId) throws SQLException {
        ArrayList<ProductDetails> products = productRepository.retrieveAvailableParentProducts(productId);
        return ResponseEntity.ok(products);
    }

    /**
     * Adds a product using the existing request validation and repository.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> addProduct(
            @RequestBody ProductInput input) throws SQLException {
        requireInventory(input);

        AddProductRequest request
                = new AddProductRequest(input.name(), input.sellPrice(), input.inventory(), input.parentId());

        int productId = productRepository.add(request, input.tags(), input.tagColors());

        if (productId == 0) {
            throw new SQLException("The repository did not add the product.");
        }

        // Add the materials, if there are any
        if (input.materials != null) {
            System.out.println(input.materials.size());
            for (TrimmedMaterialDetails md : input.materials) {
                AddMaterialRequest mdRequest
                        = new AddMaterialRequest(md.materialName(), productId, md.materialCost(), "unit", new BigDecimal(0), new BigDecimal(0));
                materialRepository.addMaterial(mdRequest);
            }
        }

        return ResponseEntity.status(201)
                .body(Map.of("message", "Product added successfully.", "productId", String.valueOf(productId)));
    }

    /**
     * Saves edits and reports when the selected product no longer exists.
     */
    @PutMapping("/{productId}")
    public ResponseEntity<Map<String, String>> editProduct(
            @PathVariable("productId") int productId,
            @RequestBody ProductInput input) throws SQLException {
        requireInventory(input);

        EditProductRequest request
                = new EditProductRequest(
                        productId, input.name(), input.sellPrice(), input.inventory(), input.parentId());

        if (!productRepository.update(request, input.tags(), input.tagColors())) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "This product no longer exists."));
        }

        // Remove previous materials and add the new materials, if there are any
        if (input.materials != null) {
            System.out.println(input.materials.size());
            materialRepository.deleteAllMaterials(productId);
            for (TrimmedMaterialDetails md : input.materials) {
                AddMaterialRequest mdRequest
                        = new AddMaterialRequest(md.materialName(), productId, md.materialCost(), "unit", new BigDecimal(0), new BigDecimal(0));
                materialRepository.addMaterial(mdRequest);
            }
        }

        return ResponseEntity.ok(
                Map.of("message", "Product updated successfully."));
    }

    /**
     * Query the database for products that contain the input
     */
    @PutMapping("/search")
    public ResponseEntity<Map<String, ArrayList<ProductDetails>>> queryProduct(@RequestBody String query) throws SQLException {
        ArrayList<ProductDetails> products = productRepository.searchProducts(query);
        return ResponseEntity.ok().body(Map.of("data", products));
    }

    ;

  /** Archives the selected product. **/
  @PutMapping("/archive")
    public ResponseEntity<Map<String, String>> archiveProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        ArchiveProductRequest request
                = new ArchiveProductRequest(selector.productId);

        if (!productRepository.archive(request)) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "This product no longer exists."));
        }

        return ResponseEntity.ok(
                Map.of("message", "Product archived successfully."));
    }

    /**
     * Restores the selected product without changing stock or related records.
     */
    @PutMapping("/restore")
    public ResponseEntity<Map<String, String>> restoreProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        if (!productRepository.restore(new ArchiveProductRequest(selector.productId()))) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "This product no longer exists."));
        }
        return ResponseEntity.ok(Map.of("message", "Product restored successfully."));
    }

    /**
     * Deletes the selected product. *
     */
    @PutMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        DeleteProductRequest request
                = new DeleteProductRequest(selector.productId);

        if (!productRepository.delete(request)) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "This product no longer exists."));
        }

        return ResponseEntity.ok(
                Map.of("message", "Product deleted successfully."));
    }

    /**
     * Deletes the selected product. *
     */
    @PutMapping("/duplicate")
    public ResponseEntity<Map<String, String>> duplicateProduct(
            @RequestBody ProductSelector selector) throws SQLException {
        DuplicateProductRequest request
                = new DuplicateProductRequest(selector.productId);

        if (!productRepository.duplicate(request)) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "This product no longer exists."));
        }

        return ResponseEntity.ok(
                Map.of("message", "Product duplicated successfully."));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Map<String, String>> registerSale(
            @PathVariable("productId") int productId,
            @RequestBody SaleInput input) throws SQLException {

        if (input == null || input.quantity() == null) {
            throw new IllegalArgumentException("Quantity is required.");
        }

        WholeSaleRequest request = new WholeSaleRequest(
                productId, input.quantity(), input.unitPrice(), input.discount(), input.email());

        if (!saleRepository.register(request)) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "This product no longer exists."));
        }

        return ResponseEntity.status(201)
                .body(Map.of("message", "Sale registered successfully."));
    }

    /**
     * Input for the Add Material form.
     */
    public record MaterialInput(String materialName, Integer productId, BigDecimal materialCost, String unitTerm, BigDecimal unitQuantity, BigDecimal usedQuantity) {}

    /**
     * Selector shared by the Delete Product, Archive Product, and Duplicate
     * Product forms.
     */
    public record MaterialSelector(String materialName, int productId) {}

    /**
     * Adds a material. *
     */
    @PutMapping("/add")
    public ResponseEntity<Map<String, String>> addMaterial(
            @RequestBody MaterialInput input) throws SQLException {

        AddMaterialRequest request
                = new AddMaterialRequest(input.materialName(), input.productId(), input.materialCost(),
                        input.unitTerm(), input.unitQuantity(), input.usedQuantity());

        if (!materialRepository.addMaterial(request)) {
            throw new SQLException("The repository did not add the material.");
        }

        return ResponseEntity.status(201)
                .body(Map.of("message", "Material added successfully."));
    }

    /**
     * Deletes a material. *
     */
    @PutMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteMaterial(
            @RequestBody MaterialSelector selector) throws SQLException {

        DeleteMaterialRequest request
                = new DeleteMaterialRequest(selector.materialName(), selector.productId());

        if (!materialRepository.deleteMaterial(request)) {
            throw new SQLException("The repository did not remove the material.");
        }

        return ResponseEntity.status(201)
                .body(Map.of("message", "Material deleted successfully."));
    }

    /**
     * Retrieve materials for a given product. *
     */
    @GetMapping("/list/{productId}")
    public ResponseEntity<ArrayList<TrimmedMaterialDetails>> retrieveMaterials(
            @PathVariable("productId") int productId) throws SQLException {
        ArrayList<TrimmedMaterialDetails> materials = materialRepository.retrieveMaterials(productId);
        return ResponseEntity.ok(materials);
    }

    
    public TagRepository.Catalog getTagCatalog() throws SQLException {
        return tagRepository.catalog();
    }

    /**
     * Logs technical failures and returns a message suitable for the form.
     */
    @ExceptionHandler({SQLException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleStorageFailure(
            Exception exception) {
        LOGGER.error("Products Page Service operation failed.", exception);

        return ResponseEntity.status(500)
                .body(Map.of("message", "Could not complete the request. Please try again."));
    }

    private static void requireInventory(ProductInput input) {
        if (input.inventory() == null) {
            throw new IllegalArgumentException("Inventory is required.");
        }
    }
}
