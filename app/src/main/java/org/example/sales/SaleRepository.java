package org.example.sales;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.example.database.DatabaseConnection;

/** Reads and writes sales and corresponding stored product information. */
public final class SaleRepository {
    /**
     * Records sale registered, time sale is made, and the previous product state.
     * Updates the product's inventory size accordingly.
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
                // The current schema has no automatic history ID generator.
                // Hold this lock until commit so concurrent history writes cannot
                // allocate the same next ID.
                try (PreparedStatement statement =
                    connection.prepareStatement(
                        "LOCK TABLE public.\"ProductHistory\" IN EXCLUSIVE MODE")) {
                    statement.setQueryTimeout(5);
                    statement.execute();
                }

                try (PreparedStatement statement =
                    connection.prepareStatement(
                        "LOCK TABLE public.\"SaleHistory\" IN EXCLUSIVE MODE")) {
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
                    SET inventory = ?
                    WHERE product_id = ?
                    """;

                try (PreparedStatement statement =
                    connection.prepareStatement(updateProductSql)) {
                    statement.setInt(1, request.inventory() - request.quantity());
                    statement.setInt(2, request.productId());
                    statement.setQueryTimeout(5);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Could not update the product.");
                    }
                }

                int newSaleId = (int) Math.random() * Integer.MAX_VALUE; // Generate a random sale ID.

                // Record sale made in sale history table.
                String saleHistorySql =
                    """
                    INSERT INTO public."SaleHistory"
                        (sale_id, product_id, date, time, discount, email, 
                        change_id, change_date, change_time)
                    VALUES(?, ?, CURRENT_DATE, LOCALTIMESTAMP(0), ?, ?,
                        (SELECT COALESCE(MAX(change_id), 0) + 1
                            FROM ONLY public."SaleHistory"),
                        CURRENT_DATE, LOCALTIMESTAMP(0))
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM ONLY public."SaleHistory"
                        WHERE sale_id = ?
                    )
                    """;

                try (PreparedStatement statement =
                    connection.prepareStatement(saleHistorySql)) {
                    statement.setInt(1, newSaleId);
                    statement.setInt(2, request.productId());
                    statement.setBigDecimal(3, request.discount());
                    statement.setString(4, request.email());
                    statement.setInt(5, newSaleId);
                    statement.setQueryTimeout(5);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Could not record the product's previous state.");
                    }
                }

                // Record sale made in sale table.
                String saleSql =
                    """
                    INSERT INTO public."Sale"
                        (sale_id, product_id, date, time, discount, email)
                    SELECT sale_id, product_id, date, time, discount, email
                    FROM ONLY public."SaleHistory"
                    WHERE sale_id = ?
                    """;

                try (PreparedStatement statement =
                    connection.prepareStatement(saleSql)) {
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
