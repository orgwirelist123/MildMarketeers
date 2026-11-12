package org.example.sales;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.example.database.DatabaseConnection;

/** Saves a whole-sale discount and inventory changes in one transaction. */
public final class WholeSaleRepository {

  /**
   * Saves the sale and both histories atomically.
   *
   * @return true on success, or false if the product does not exist
   */
  public boolean register(WholeSaleRequest request) throws SQLException {
    Objects.requireNonNull(request, "Sale request is required.");

    try (Connection connection = DatabaseConnection.open()) {
      connection.setAutoCommit(false);

      try {
        // History IDs currently use MAX(change_id) + 1.
        // Acquire history locks before locking the product, matching product edits.
        execute(connection,
            "LOCK TABLE public.\"ProductHistory\" IN EXCLUSIVE MODE");
        execute(connection,
            "LOCK TABLE public.\"SaleHistory\" IN EXCLUSIVE MODE");

        int inventory;
        int unitsSold;

        try (PreparedStatement statement = prepare(connection, """
            SELECT inventory, units_sold, archived
            FROM ONLY public."Product"
            WHERE product_id = ?
            FOR UPDATE
            """)) {
          statement.setInt(1, request.productId());

          try (var result = statement.executeQuery()) {
            if (!result.next()) {
              connection.rollback();
              return false;
            }

            if (result.getBoolean("archived")) {
              throw new IllegalArgumentException(
                  "Cannot register a sale for an archived product.");
            }

            inventory = result.getInt("inventory");
            unitsSold = result.getInt("units_sold");
          }
        }

        if (request.quantity() > inventory) {
          throw new IllegalArgumentException(
              "Not enough stock. Available quantity: " + inventory + ".");
        }

        long updatedUnitsSold = (long) unitsSold + request.quantity();
        if (updatedUnitsSold > Integer.MAX_VALUE) {
          throw new IllegalArgumentException(
              "This sale would exceed the supported units-sold limit.");
        }

        try (PreparedStatement statement = prepare(connection, """
            INSERT INTO public."ProductHistory"
                (product_id, name, sell_price, units_sold, inventory,
                 parent_id, archived, change_id, change_date, change_time)
            SELECT product_id, name, sell_price, units_sold, inventory,
                   parent_id, archived,
                   (SELECT COALESCE(MAX(change_id), 0) + 1
                    FROM ONLY public."ProductHistory"),
                   CURRENT_DATE, LOCALTIMESTAMP(0)
            FROM ONLY public."Product"
            WHERE product_id = ?
            """)) {
          statement.setInt(1, request.productId());
          requireOne(statement.executeUpdate(), "record product history");
        }

        int saleId = insertSale(connection, request);

        try (PreparedStatement statement = prepare(connection, """
            INSERT INTO public."SaleHistory"
                (sale_id, product_id, date, time, quantity, unit_price,
                 discount, email, change_id, change_date, change_time)
            SELECT sale_id, product_id, date, time, quantity, unit_price,
                   discount, email,
                   (SELECT COALESCE(MAX(change_id), 0) + 1
                    FROM ONLY public."SaleHistory"),
                   CURRENT_DATE, LOCALTIME(0)
            FROM ONLY public."Sale"
            WHERE sale_id = ?
            """)) {
          statement.setInt(1, saleId);
          requireOne(statement.executeUpdate(), "record sale history");
        }

        try (PreparedStatement statement = prepare(connection, """
            UPDATE ONLY public."Product"
            SET inventory = ?, units_sold = ?
            WHERE product_id = ?
            """)) {
          statement.setInt(1, inventory - request.quantity());
          statement.setInt(2, (int) updatedUnitsSold);
          statement.setInt(3, request.productId());
          requireOne(statement.executeUpdate(), "update product stock");
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

  private static int insertSale(Connection connection, WholeSaleRequest request)
      throws SQLException {
    // ON CONFLICT handles a randomly generated ID already being in use.
    for (int attempt = 0; attempt < 10; attempt++) {
      int saleId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

      try (PreparedStatement statement = prepare(connection, """
          INSERT INTO public."Sale"
              (sale_id, product_id, date, time, quantity, unit_price, discount, email)
          VALUES (?, ?, CURRENT_DATE, LOCALTIME(0), ?, ?, ?, ?)
          ON CONFLICT (sale_id) DO NOTHING
          """)) {
        statement.setInt(1, saleId);
        statement.setInt(2, request.productId());
        statement.setInt(3, request.quantity());
        // New contract: unit_price is BEFORE the whole-sale discount.
        statement.setBigDecimal(4, request.unitPrice());
        statement.setBigDecimal(5, request.discount());
        statement.setString(6, request.email());

        if (statement.executeUpdate() == 1) {
          return saleId;
        }
      }
    }

    throw new SQLException("Could not allocate a unique sale ID.");
  }

  private static PreparedStatement prepare(Connection connection, String sql)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    statement.setQueryTimeout(5);
    return statement;
  }

  private static void execute(Connection connection, String sql)
      throws SQLException {
    try (PreparedStatement statement = prepare(connection, sql)) {
      statement.execute();
    }
  }

  private static void requireOne(int affectedRows, String operation)
      throws SQLException {
    if (affectedRows != 1) {
      throw new SQLException("Could not " + operation + ".");
    }
  }
}
