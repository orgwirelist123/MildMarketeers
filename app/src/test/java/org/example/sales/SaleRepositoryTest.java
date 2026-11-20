package org.example.sales;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ThreadLocalRandom;

import org.example.database.DatabaseConnection;
import org.example.product.ProductDetails;
import org.example.product.ProductRepository;
import org.junit.Test;

public class SaleRepositoryTest {

	// Checks that sale was successfully registered
	@Test
	public void registerSaleAndPreservesPreviousProductState() throws Exception {
		assumeTrue(
			"Set MM_DB_TEST=true to run the database integration test.",
			"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));

		int productId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1);
		boolean inserted = false;

		try (Connection connection = DatabaseConnection.open()) {
			connection.setAutoCommit(true);

			try {
				try(PreparedStatement statement =
					connection.prepareStatement(
						"""
						INSERT INTO public."Product"
							(product_id, name, sell_price, units_sold, inventory, parent_id)
						VALUES (?, 'Cookie', 12, 5, 20, NULL)
						ON CONFLICT (product_id) DO NOTHING
						""")) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);
					inserted = statement.executeUpdate() == 1;
				}

				assertTrue("Test ID already exists; rerun the test.", inserted);

				SaleRepository saleRepository = new SaleRepository();
				ProductRepository prodRepository = new ProductRepository();
				RegisterSaleRequest request = new RegisterSaleRequest(productId, 5, 20, 
					new BigDecimal("2"), new BigDecimal("2"), new BigDecimal("2"), 0, null);

				assertTrue("The register should succeed.", saleRepository.registerSale(request));

				// Check Product table
				ProductDetails actual = prodRepository.findById(productId).orElseThrow();

				assertEquals(15, actual.inventory());

				try (PreparedStatement statement =
					connection.prepareStatement(
						"""
						SELECT units_sold
						FROM ONLY public."Product"
						WHERE product_id = ?
						""")) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);

					try (var result = statement.executeQuery()) {
						assertTrue(result.next());
						assertEquals(10, result.getInt("units_sold"));
					}
				}

				// Check ProductHistory table
				try (PreparedStatement statement =
					connection.prepareStatement(
						"""
						SELECT name, sell_price, inventory, units_sold,
							change_date, change_time
						FROM ONLY public."ProductHistory"
						WHERE product_id = ?
						""")) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);

					try (var result = statement.executeQuery()) {
						assertTrue("The previous state should be recorded.", result.next());

						assertEquals("Cookie", result.getString("name"));
						assertEquals(0, new BigDecimal("12").compareTo(result.getBigDecimal("sell_price")));
						assertEquals(20, result.getInt("inventory"));
						assertEquals(5, result.getInt("units_sold"));
						assertTrue(result.getDate("change_date") != null);
						assertTrue(result.getTimestamp("change_time") != null);
						assertTrue("Exactly one history row should exist.", !result.next());
					}
				}

				// Check Sale table
				try (PreparedStatement statement =
					connection.prepareStatement(
						"""
						SELECT date, time, discount, email
						FROM ONLY public."Sale"
						WHERE product_id = ?
						""")) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);

					try (var result = statement.executeQuery()) {
						assertTrue("The sale should be recorded.", result.next());

						assertTrue(result.getDate("date") != null);
						assertTrue(result.getTimestamp("time") != null);
						assertEquals(0, result.getInt("discount"));
						assertEquals(null, result.getInt("email"));
						assertTrue("Exactly one history row should exist.", !result.next());
					}
				}

				// Check SaleHistory table
				try (PreparedStatement statement =
					connection.prepareStatement(
						"""
					   SELECT date, time, discount, email,
							change_date, change_time
						FROM ONLY public."SaleHistory"
						WHERE product_id = ?
						""")) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);

					try (var result = statement.executeQuery()) {
						assertTrue("The sale change should be recorded.", result.next());

						assertTrue(result.getDate("date") != null);
						assertTrue(result.getTimestamp("time") != null);
						assertEquals(0, result.getInt("discount"));
						assertEquals(null, result.getInt("email"));
						assertTrue(result.getDate("change_date") != null);
						assertTrue(result.getTimestamp("change_time") != null);
						assertTrue("Exactly one history row should exist.", !result.next());
					}
				}
			} 
			finally {
				if (inserted) {
					try (PreparedStatement statement =
						connection.prepareStatement(
							"DELETE FROM ONLY public.\"ProductHistory\" WHERE product_id = ?")) {
						statement.setQueryTimeout(5);
						statement.setInt(1, productId);
						statement.executeUpdate();
					}

					try (PreparedStatement statement =
						connection.prepareStatement(
							"DELETE FROM ONLY public.\"Product\" WHERE product_id = ?")) {
						statement.setQueryTimeout(5);
						statement.setInt(1, productId);
						statement.executeUpdate();
					}

					try (PreparedStatement statement =
						connection.prepareStatement(
							"DELETE FROM ONLY public.\"SaleHistory\" WHERE product_id = ?")) {
						statement.setQueryTimeout(5);
						statement.setInt(1, productId);
						statement.executeUpdate();
					}

					try (PreparedStatement statement =
						connection.prepareStatement(
							"DELETE FROM ONLY public.\"Sale\" WHERE product_id = ?")) {
						statement.setQueryTimeout(5);
						statement.setInt(1, productId);
						statement.executeUpdate();
					}                    
				}
			}
		}
	}

	// Checks for unsuccessful sale register when product doesn't exist in database
	@Test
	public void missingProductDoesNotCreateSale() throws Exception {
		assumeTrue(
			"Set MM_DB_TEST=true to run the database integration test.",
			"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));

		int productId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1);

		try (Connection connection = DatabaseConnection.open()) {
			String countSql =
				"""
				SELECT
					(SELECT COUNT(*) FROM ONLY public."Product"
					WHERE product_id = ?) AS products,
					(SELECT COUNT(*) FROM ONLY public."ProductHistory"
					WHERE product_id = ?) AS history
				""";

			try (PreparedStatement statement = connection.prepareStatement(countSql)) {
				statement.setQueryTimeout(5);
				statement.setInt(1, productId);
				statement.setInt(2, productId);

				try (var result = statement.executeQuery()) {
					assertTrue(result.next());
					assertEquals("Test ID is already used; rerun.", 0, result.getInt("products"));
					assertEquals("Test ID has history; rerun.", 0, result.getInt("history"));
				}

				SaleRepository repository = new SaleRepository();
				RegisterSaleRequest request =
					new RegisterSaleRequest(
						productId, 5, 20, new BigDecimal("2"), new BigDecimal("2"), 
						new BigDecimal("2"), 0, null);

				assertEquals(false, repository.registerSale(request));

				try (var result = statement.executeQuery()) {
					assertTrue(result.next());
					assertEquals(0, result.getInt("products"));
					assertEquals(0, result.getInt("history"));
				}
			}
		}
	}
}
