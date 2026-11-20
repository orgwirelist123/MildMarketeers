package org.example.materials;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.example.database.DatabaseConnection;

public final class MaterialRepository {
	
	/**
	 * Retrieves the product details of all the products in the database.
	 *
	 * @param request validated material data
	 * @return true if the material was added, false otherwise
	 * @throws SQLException if the database query fails
	 */
	public boolean addMaterial(AddMaterialRequest request) throws SQLException {
		String sql =
			"""
			INSERT INTO public."Material"
				(material_name, product_id, material_cost, unit_term, unit_quantity, used_quantity)
			VALUES
				(?, ?, ?, ?, ?, ?)
			""";

		try (Connection connection = DatabaseConnection.open(); ) {
			
			boolean inUse = true;

			try (PreparedStatement statement =
				connection.prepareStatement(
					"""
					SELECT material_name, product_id
					FROM ONLY public."Material"
					WHERE material_name = ? AND product_id = ?
					FOR UPDATE
					""")) {
				statement.setString(1, request.materialName());
				statement.setInt(2, request.productId());
				statement.setQueryTimeout(5);

				try (ResultSet result = statement.executeQuery()) {
					if (!result.next()) {
						inUse = false;
					}
				}
			}

			if (inUse) {
				throw new SQLException("A material already exists with this primary key tuple.");
			}

			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.setString(1, request.materialName());
				statement.setInt(2, request.productId());
				statement.setBigDecimal(3, request.materialCost());
				statement.setString(4, request.unitTerm());
				statement.setBigDecimal(5, request.unitQuantity());
				statement.setBigDecimal(6, request.usedQuantity());
				
				statement.setQueryTimeout(5);

				if (statement.executeUpdate() != 1) {
					throw new SQLException("Could not add the material.");
				}
			}
		}
		return true;
	}

	/**
	 * Retrieves the material details for the given product in the 'Material' table
	 *
	 * @param productId the product identifier to get materials for
	 * @return the list of materials for the given product identifier
	 * @throws SQLException if the database query fails
	 */
	public ArrayList<TrimmedMaterialDetails> retrieveMaterials(int productId) throws SQLException {
		String sql =
			"""
			SELECT material_name, material_cost
			FROM ONLY public."Material"
			WHERE product_id = ?;
			""";

		ArrayList<TrimmedMaterialDetails> products = new ArrayList<TrimmedMaterialDetails>();
		try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, productId);
			statement.setQueryTimeout(5);

			try (ResultSet results = statement.executeQuery()) {

				while(results.next())
				{
					String materialName = results.getString("material_name");
					BigDecimal materialCost = results.getBigDecimal("material_cost");

					products.add(
						new TrimmedMaterialDetails(materialName, materialCost));
				}

				return products;
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}

		} catch (Exception e) {
		// TODO: handle exception
		}
		// return products;
		return null;
	}

	/**
	 * Deletes the material for a given product
	 *
	 * @param request validated material targeter
	 * @return true if the material was added, false otherwise
	 * @throws SQLException if the database query fails
	 */
	public boolean deleteMaterial(DeleteMaterialRequest request) throws SQLException {
		String sql =
			"""
			DELETE FROM ONLY public."Material"
			WHERE material_name = ? AND product_id = ?
			""";

		try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, request.materialName());
			statement.setInt(2, request.productId());
			statement.setQueryTimeout(5);

			if (statement.executeUpdate() != 1) {
				throw new SQLException("Could not delete the material.");
			}
		}

		return true;
	}

	/**
	 * Deletes the material for a given product
	 *
	 * @param request validated material targeter
	 * @return true if the material was added, false otherwise
	 * @throws SQLException if the database query fails
	 */
	public boolean deleteAllMaterials(int productId) throws SQLException {
		String sql =
			"""
			DELETE FROM ONLY public."Material"
			WHERE product_id = ?
			""";

		try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, productId);
			statement.setQueryTimeout(5);

			statement.executeUpdate();
		}

		return true;
	}


}
