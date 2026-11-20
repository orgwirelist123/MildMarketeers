package org.example.product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.example.database.DatabaseConnection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import org.junit.Test;

/** Integration tests for retrieving current product details. */
public class ProductRepositoryTest {

	@Test
	public void retrievesExistingProductDetails() throws Exception {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));

		int productId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1);
		boolean inserted = false;

		try (Connection connection = DatabaseConnection.open()) {
			// Commit the fixture so the repository's separate connection can see it.
			connection.setAutoCommit(true);

			try {
				String insertSql =
						"""
						INSERT INTO public."Product"
								(product_id, name, sell_price, units_sold, inventory, parent_id)
						VALUES (?, ?, ?, 0, ?, NULL)
						ON CONFLICT (product_id) DO NOTHING
						""";

				try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);
					statement.setString(2, "Repository Test Cookie");
					statement.setBigDecimal(3, new BigDecimal("12"));
					statement.setInt(4, 20);
					inserted = statement.executeUpdate() == 1;
				}

				assertTrue("Test ID already exists; rerun the test.", inserted);

				ProductRepository repository = new ProductRepository();
				Optional<ProductDetails> result = repository.findById(productId);

				assertTrue("The test product should be found.", result.isPresent());

				ProductDetails actual = result.get();
				assertEquals(productId, actual.productId());
				assertEquals("Repository Test Cookie", actual.name());
				assertEquals(0, new BigDecimal("12").compareTo(actual.sellPrice()));
				assertEquals(20, actual.inventory());
			} finally {
				// Remove only the product successfully inserted by this test.
				if (inserted) {
					try (PreparedStatement statement =
							connection.prepareStatement(
									"DELETE FROM ONLY public.\"Product\" WHERE product_id = ?")) {
						statement.setQueryTimeout(5);
						statement.setInt(1, productId);
						statement.executeUpdate();
					}
				}
			}
		}
	}
		@Test
	public void updatesProductAndPreservesPreviousState() throws Exception {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));

		int productId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1);
		boolean inserted = false;

		try (Connection connection = DatabaseConnection.open()) {
			connection.setAutoCommit(true);

			try {
				try (PreparedStatement statement =
						connection.prepareStatement(
								"""
								INSERT INTO public."Product"
										(product_id, name, sell_price, units_sold, inventory, parent_id)
								VALUES (?, 'Original Cookie', 12, 3, 20, NULL)
								ON CONFLICT (product_id) DO NOTHING
								""")) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);
					inserted = statement.executeUpdate() == 1;
				}

				assertTrue("Test ID already exists; rerun the test.", inserted);

				ProductRepository repository = new ProductRepository();
				EditProductRequest request =
						new EditProductRequest(
								productId, "Updated Cookie", new BigDecimal("15"), 25, 0);

				assertTrue("The update should succeed.", repository.update(request));

				ProductDetails actual =
						repository.findById(productId).orElseThrow();

				assertEquals("Updated Cookie", actual.name());
				assertEquals(0, new BigDecimal("15").compareTo(actual.sellPrice()));
				assertEquals(25, actual.inventory());

				try (PreparedStatement statement =
						connection.prepareStatement(
								"""
								SELECT units_sold, parent_id
								FROM ONLY public."Product"
								WHERE product_id = ?
								""")) {
					statement.setQueryTimeout(5);
					statement.setInt(1, productId);

					try (var result = statement.executeQuery()) {
						assertTrue(result.next());
						assertEquals(3, result.getInt("units_sold"));
						assertEquals(null, result.getObject("parent_id"));
					}
				}

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
						assertEquals("Original Cookie", result.getString("name"));
						assertEquals(
								0, new BigDecimal("12").compareTo(result.getBigDecimal("sell_price")));
						assertEquals(20, result.getInt("inventory"));
						assertEquals(3, result.getInt("units_sold"));
						assertTrue(result.getDate("change_date") != null);
						assertTrue(result.getTimestamp("change_time") != null);
						assertTrue("Exactly one history row should exist.", !result.next());
					}
				}
			} finally {
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
				}
			}
		}
	}
		@Test
	public void missingProductDoesNotCreateHistory() throws Exception {
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

				ProductRepository repository = new ProductRepository();
				EditProductRequest request =
						new EditProductRequest(
								productId, "Missing Product", new BigDecimal("10"), 5, 0);

				assertEquals(false, repository.update(request));

				try (var result = statement.executeQuery()) {
					assertTrue(result.next());
					assertEquals(0, result.getInt("products"));
					assertEquals(0, result.getInt("history"));
				}
			}
		}
	}
}
