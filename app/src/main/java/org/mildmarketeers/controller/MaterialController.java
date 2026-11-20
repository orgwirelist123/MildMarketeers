package org.mildmarketeers.controller;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import org.example.materials.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Connects material forms to the material repository. */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

	private static final Logger LOGGER =
	LoggerFactory.getLogger(MaterialController.class);

	private final MaterialRepository repository = new MaterialRepository();

	/** Input for the Add Material form. */
	public record MaterialInput(String materialName, Integer productId, BigDecimal materialCost, String unitTerm, BigDecimal unitQuantity, BigDecimal usedQuantity) {}
	/** Selector shared by the Delete Product, Archive Product, and Duplicate Product forms. */
	public record MaterialSelector(String materialName, int productId) {}

	
	/** Adds a material. **/
	@PutMapping("/add")
	public ResponseEntity<Map<String, String>> addMaterial(
		@RequestBody MaterialInput input) throws SQLException {

		AddMaterialRequest request =
			new AddMaterialRequest(input.materialName(), input.productId(), input.materialCost(),
				input.unitTerm(), input.unitQuantity(), input.usedQuantity());

		if (!repository.addMaterial(request)) {
			throw new SQLException("The repository did not add the material.");
		}

		return ResponseEntity.status(201)
			.body(Map.of("message", "Material added successfully."));
	}
	
	/** Deletes a material. **/
	@PutMapping("/delete")
	public ResponseEntity<Map<String, String>> deleteMaterial(
		@RequestBody MaterialSelector selector) throws SQLException {

		DeleteMaterialRequest request =
			new DeleteMaterialRequest(selector.materialName(), selector.productId());

		if (!repository.deleteMaterial(request)) {
			throw new SQLException("The repository did not remove the material.");
		}

		return ResponseEntity.status(201)
			.body(Map.of("message", "Material deleted successfully."));
	}
	
	/** Retrieve materials for a given product. **/
	@GetMapping("/list/{productId}")
	public ResponseEntity<ArrayList<TrimmedMaterialDetails>> retrieveMaterials(
		@PathVariable("productId") int productId) throws SQLException {
		ArrayList<TrimmedMaterialDetails> materials = repository.retrieveMaterials(productId);
		return ResponseEntity.ok(materials);
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
		LOGGER.error("Material operation failed.", exception);

		return ResponseEntity.status(500)
			.body(Map.of("message", "Could not complete the request. Please try again."));
	}
}
