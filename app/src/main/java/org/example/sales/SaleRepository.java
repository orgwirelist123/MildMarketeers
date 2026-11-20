package org.example.sales;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.example.database.DatabaseConnection;

/** Reads and writes sales and corresponding stored product information. */
public final class SaleRepository {

	/**
	 * Updates product inventory and units sold according to sale made. 
	 * Records previous product state.
	 * 
	 * @param request validated product changes
	 * @return true if the product was updated, or false if it does not exist
	 * @throws SQLException if saving or recording history fails
	 */
	public boolean updateProduct(RegisterSaleRequest request) throws SQLException {
		java.util.Objects.requireNonNull(request, "Register sale request is required.");

		try (Connection connection = DatabaseConnection.open()) {
			connection.setAutoCommit(false);

			try {
				// The current schema has no automatic history ID generator.
				// Hold this lock until commit so concurrent history writes cannot
				// allocate the same next ID.
				try (PreparedStatement statement =
					connection.prepareStatement(
						"LOCK TABLE public.\"ProductHistory\" IN EXCLUSIVE MODE")) {
					statement.setQueryTimeout(5);
					statement.execute();
				}
				
				// Lock the current product so its saved history matches the state
				// immediately before this update.
				try (PreparedStatement statement =
					connection.prepareStatement(
						"""
						SELECT product_id
						FROM ONLY public."Product"
						WHERE product_id = ?
						FOR UPDATE
						""")) {
					statement.setInt(1, request.productId());
					statement.setQueryTimeout(5);

					try (ResultSet result = statement.executeQuery()) {
						if (!result.next()) {
							connection.rollback();
							return false;
						}
					}   
				}

				// Record product state into product history table before registering sale.
				String productHistorySql =
					"""
					INSERT INTO public."ProductHistory"
						(product_id, name, sell_price, units_sold, inventory,
						parent_id, change_id, change_date, change_time)
					SELECT product_id, name, sell_price, units_sold, inventory,
						parent_id,
						(SELECT COALESCE(MAX(change_id), 0) + 1
							FROM ONLY public."ProductHistory"),
						CURRENT_DATE, LOCALTIMESTAMP(0)
					FROM ONLY public."Product"
					WHERE product_id = ?
					""";

				try (PreparedStatement statement =
					connection.prepareStatement(productHistorySql)) {
					statement.setInt(1, request.productId());
					statement.setQueryTimeout(5);

					if (statement.executeUpdate() != 1) {
						throw new SQLException("Could not record the product's previous state.");
					}
				}

				// Update product's inventory size after sale by subtracting the sold quantity
				// from the current inventory size.
				String updateProductSql =
					"""
					UPDATE ONLY public."Product"
					SET units_sold = ?, inventory = ?
					WHERE product_id = ?
					""";

				try (PreparedStatement statement =
					connection.prepareStatement(updateProductSql)) {
						statement.setInt(1, request.pastUnitsSold() + request.soldQuantity());
						statement.setInt(2, request.inventory() - request.soldQuantity());
						statement.setInt(3, request.productId());
						statement.setQueryTimeout(5);

					if (statement.executeUpdate() != 1) {
						throw new SQLException("Could not update the product.");
					}
				}
				connection.commit();
				return true;
			} catch (SQLException | RuntimeException exception) {
				try {
					connection.rollback();
				} catch (SQLException rollbackException) {
					exception.addSuppressed(rollbackException);
				}
				throw exception;
			}
		}
	}

	/**
	 * Records sale registered and time sale is made.
	 * 
	 * @param request validated sale details
	 * @return true if sale was registered successfully, false if otherwise
	 * @throws SQLException if saving or recording history fails
	 */
	public boolean registerSale(RegisterSaleRequest request) throws SQLException {
		java.util.Objects.requireNonNull(request, "Register sale request is required.");

		try (Connection connection = DatabaseConnection.open()) {
			connection.setAutoCommit(false);

			try {
				try (PreparedStatement statement =
					connection.prepareStatement(
						"LOCK TABLE public.\"SaleHistory\" IN EXCLUSIVE MODE")) {
					statement.setQueryTimeout(5);
					statement.execute();
				}

				int newSaleId;
				boolean inUse = true;

				do{
					// Generate a new productId for the new product.
					newSaleId = java.util.concurrent.ThreadLocalRandom.current()
						.nextInt(1, Integer.MAX_VALUE);

					// Check to see if a product with the ID exists.
					try (PreparedStatement statement =
						connection.prepareStatement(
							"""
							SELECT sale_id
							FROM ONLY public."Sale"
							WHERE sale_id = ?
							FOR UPDATE
							""")) {
						statement.setInt(1, newSaleId);
						statement.setQueryTimeout(5);

						try (ResultSet result = statement.executeQuery()) {
							if (!result.next()) {
								inUse = false;
							}
						}
					}
				}while(inUse);                

				// Record sale made in sale table.
				String saleSql =
					"""
					INSERT INTO public."Sale"
						(sale_id, product_id, date, time, quantity, unit_price, discount, email)
					VALUES (?, ?, CURRENT_DATE, LOCALTIMESTAMP(0), ?, ?, ?, ?)
					""";

				try (PreparedStatement statement = connection.prepareStatement(saleSql)) {
					BigDecimal normalTotalPrice = BigDecimal.valueOf(request.soldQuantity()).multiply(request.originalUnitPrice());

					statement.setInt(1, newSaleId);
					statement.setInt(2, request.productId());
					statement.setInt(3, request.soldQuantity());
					statement.setBigDecimal(4, request.sellPrice());
					statement.setBigDecimal(5, normalTotalPrice.subtract(request.totalPrice()));
					statement.setString(6, request.email());
					statement.setQueryTimeout(5);

					if (statement.executeUpdate() != 1) {
						throw new SQLException("Could not record the product's previous state.");
					}
				}

				// Record sale made in sale history table.
				String saleHistorySql =
					"""
					INSERT INTO public."SaleHistory"
						(sale_id, product_id, date, time, quantity, unit_price, discount, email, 
						change_id, change_date, change_time)
					SELECT sale_id, product_id, date, time, quantity, unit_price, discount, email,
						(SELECT COALESCE(MAX(change_id), 0) + 1
						FROM ONLY public."SaleHistory"),
						date, time
					FROM ONLY public."Sale"
					WHERE sale_id = ?
					""";

				try (PreparedStatement statement =
					connection.prepareStatement(saleHistorySql)) {
						statement.setInt(1, newSaleId);
						statement.setQueryTimeout(5);

					if (statement.executeUpdate() != 1) {
						throw new SQLException("Could not record the product's previous state.");
					}
				}

				connection.commit();
				return true;
			} catch (SQLException | RuntimeException exception) {
				try {
					connection.rollback();
				} catch (SQLException rollbackException) {
					exception.addSuppressed(rollbackException);
				}
				throw exception;
			}
		}        
	}
}
