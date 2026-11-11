package org.example.sales;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import org.example.database.DatabaseConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Integration tests for sale, stock, and history consistency. */
public class WholeSaleRepositoryTest {

  private Connection connection;
  private int productId;
  private boolean inserted;
  private final WholeSaleRepository repository = new WholeSaleRepository();

  @Before
  public void setUp() throws Exception {
    assumeTrue(
        "Set MM_DB_TEST=true to run database integration tests.",
        "true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));

    connection = DatabaseConnection.open();
    productId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1);

    // Avoid reusing an ID with existing product or sale history.
    for (String table : new String[] {
        "Product", "ProductHistory", "Sale", "SaleHistory"}) {
      assertEquals("Test ID already exists; rerun.", 0, count(table));
    }

    try (PreparedStatement statement = prepare("""
        INSERT INTO public."Product"
            (product_id, name, sell_price, units_sold, inventory, parent_id)
        VALUES (?, 'Whole sale integration test', 10.00, 5, 10, NULL)
        """)) {
      assertEquals(1, statement.executeUpdate());
      inserted = true;
    }
  }

  @After
  public void tearDown() throws Exception {
    if (connection == null) return;

    try {
      if (inserted) {
        connection.setAutoCommit(false);
        try {
          // Delete referencing sales before their product.
          for (String table : new String[] {
              "SaleHistory", "Sale", "ProductHistory", "Product"}) {
            try (PreparedStatement statement = prepare(
                "DELETE FROM ONLY public.\"" + table + "\" WHERE product_id = ?")) {
              statement.executeUpdate();
            }
          }
          connection.commit();
        } catch (SQLException exception) {
          connection.rollback();
          throw exception;
        }
      }
    } finally {
      connection.close();
    }
  }

  @Test
  public void savesDiscountStockAndBothHistories() throws Exception {
    assertTrue(repository.register(request(3, "1.00", " buyer@example.com ")));

    assertProduct(7, 8);
    assertEquals(1, count("Sale"));
    assertEquals(1, count("SaleHistory"));
    assertEquals(1, count("ProductHistory"));

    int saleId;
    try (PreparedStatement statement = prepare("""
        SELECT sale_id, quantity, unit_price, discount, email,
               quantity * unit_price - discount AS total
        FROM ONLY public."Sale"
        WHERE product_id = ?
        """);
        var result = statement.executeQuery()) {
      assertTrue(result.next());
      saleId = result.getInt("sale_id");
      assertEquals(3, result.getInt("quantity"));
      assertEquals(new BigDecimal("10.00"), result.getBigDecimal("unit_price"));
      assertEquals(new BigDecimal("1.00"), result.getBigDecimal("discount"));
      assertEquals(new BigDecimal("29.00"), result.getBigDecimal("total"));
      assertEquals("buyer@example.com", result.getString("email"));
    }

    try (PreparedStatement statement = prepare("""
        SELECT sale_id, quantity, unit_price, discount, email,
               change_date, change_time
        FROM ONLY public."SaleHistory"
        WHERE product_id = ?
        """);
        var result = statement.executeQuery()) {
      assertTrue(result.next());
      assertEquals(saleId, result.getInt("sale_id"));
      assertEquals(3, result.getInt("quantity"));
      assertEquals(new BigDecimal("10.00"), result.getBigDecimal("unit_price"));
      assertEquals(new BigDecimal("1.00"), result.getBigDecimal("discount"));
      assertEquals("buyer@example.com", result.getString("email"));
      assertNotNull(result.getDate("change_date"));
      assertNotNull(result.getTime("change_time"));
    }

    try (PreparedStatement statement = prepare("""
        SELECT inventory, units_sold, sell_price
        FROM ONLY public."ProductHistory"
        WHERE product_id = ?
        """);
        var result = statement.executeQuery()) {
      assertTrue(result.next());
      assertEquals(10, result.getInt("inventory"));
      assertEquals(5, result.getInt("units_sold"));
      assertEquals(new BigDecimal("10.00"), result.getBigDecimal("sell_price"));
    }
  }

  @Test
  public void rejectsInsufficientStockWithoutWritingRecords() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.register(request(11, "0.00", null)));

    assertProduct(10, 5);
    assertNoSaleOrHistory();
  }

  @Test
  public void missingProductDoesNotCreateRecords() throws Exception {
    try (PreparedStatement statement = prepare(
        "DELETE FROM ONLY public.\"Product\" WHERE product_id = ?")) {
      assertEquals(1, statement.executeUpdate());
    }

    assertFalse(repository.register(request(1, "0.00", null)));
    assertEquals(0, count("Product"));
    assertNoSaleOrHistory();
  }

  @Test
  public void databaseFailureRollsBackEarlierHistoryInsert() throws Exception {
    // PostgreSQL rejects a NUL character in text. This forces the sale INSERT
    // to fail after the repository has already inserted product history.
    String invalidDatabaseText = "buyer" + (char) 0 + "@example.com";

    assertThrows(
        SQLException.class,
        () -> repository.register(request(3, "1.00", invalidDatabaseText)));

    assertProduct(10, 5);
    assertNoSaleOrHistory();
  }

  private WholeSaleRequest request(int quantity, String discount, String email) {
    return new WholeSaleRequest(
        productId, quantity, new BigDecimal("10.00"),
        new BigDecimal(discount), email);
  }

  private void assertProduct(int inventory, int unitsSold) throws Exception {
    try (PreparedStatement statement = prepare("""
        SELECT inventory, units_sold
        FROM ONLY public."Product"
        WHERE product_id = ?
        """);
        var result = statement.executeQuery()) {
      assertTrue(result.next());
      assertEquals(inventory, result.getInt("inventory"));
      assertEquals(unitsSold, result.getInt("units_sold"));
    }
  }

  private void assertNoSaleOrHistory() throws Exception {
    assertEquals(0, count("Sale"));
    assertEquals(0, count("SaleHistory"));
    assertEquals(0, count("ProductHistory"));
  }

  private int count(String table) throws Exception {
    try (PreparedStatement statement = prepare(
        "SELECT COUNT(*) FROM ONLY public.\"" + table + "\" WHERE product_id = ?");
        var result = statement.executeQuery()) {
      assertTrue(result.next());
      return result.getInt(1);
    }
  }

  private PreparedStatement prepare(String sql) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    statement.setQueryTimeout(5);
    statement.setInt(1, productId);
    return statement;
  }
}
