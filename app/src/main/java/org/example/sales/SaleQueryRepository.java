package org.example.sales;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.example.database.DatabaseConnection;

/** Reads saved sales for display without changing sale or product records. */
public final class SaleQueryRepository {

  /** Retrieves sales with their current product names, newest first. */
  public List<SaleDetails> findAll() throws SQLException {
    // Stored unit_price is before discount; subtract the whole-sale discount once.
    String sql =
        """
        SELECT s.sale_id, s.product_id, p.name AS product_name, s.date, s.time,
              s.quantity, s.quantity * s.unit_price - s.discount AS total
        FROM ONLY public."Sale" AS s
        LEFT JOIN ONLY public."Product" AS p
          ON p.product_id = s.product_id
        ORDER BY s.date DESC, s.time DESC, s.sale_id DESC
        """;

    List<SaleDetails> sales = new ArrayList<>();

    try (Connection connection = DatabaseConnection.open();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(5);

      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          String productName = result.getString("product_name");
          if (productName == null) {
            productName = "Unavailable product";
          }

          sales.add(
              new SaleDetails(
                  result.getInt("sale_id"),
                  result.getInt("product_id"),
                  productName,
                  result.getObject("date", LocalDate.class),
                  result.getObject("time", LocalTime.class),
                  result.getInt("quantity"),
                  result.getBigDecimal("total").toPlainString()));
        }
      }
    }

    return sales;
  }
}
