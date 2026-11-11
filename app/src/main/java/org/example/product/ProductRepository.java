package org.example.product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

import org.example.database.DatabaseConnection;

/** Reads and writes stored product information. */
public final class ProductRepository {

  /**
   * Retrieves the product details of all the products in the database.
   *
   * @return the product, or an empty result if it does not exist
   * @throws SQLException if the database query fails
   */
  public ArrayList<ProductDetails> retrieveProducts() throws SQLException {
    String sql =
        """
        SELECT product_id, name, sell_price, inventory
        FROM ONLY public."Product"
        """;

    String retrieveArchivedProducts = 
        """
        SELECT ph.product_id, ph.name, ph.sell_price, ph.inventory
        FROM 
          ONLY public."ProductHistory" ph
          LEFT JOIN ONLY public."Product" p
          ON ph.product_id = p.product_id
        WHERE 
          p.product_id IS NULL
          AND ph.change_time = (SELECT MAX(ph2.change_time)
            FROM ONLY public."ProductHistory" ph2
            WHERE ph.product_id = ph2.product_id
          )
        """;
    ArrayList<ProductDetails> products = new ArrayList<ProductDetails>();
    // products.add(new ProductDetails(1, "TestProduct", new BigDecimal(100.00), 5));
    // products.add(new ProductDetails(2, "TestProduct2", new BigDecimal(5.00), 10));
    try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(5);

      try (ResultSet results = statement.executeQuery()) {
        
        while(results.next())
        {
          int id = results.getInt("product_id");
          String name = results.getString("name");
          BigDecimal price = results.getBigDecimal("sell_price");
          int inventory = results.getInt("inventory");
          products.add(new ProductDetails(id, name, price, inventory, false));
        }

        try (PreparedStatement archiveRetrieval = connection.prepareStatement(retrieveArchivedProducts)) {
          ResultSet archiveResults = archiveRetrieval.executeQuery();

          while(archiveResults.next())
          {
            int id = archiveResults.getInt("product_id");
            String name = archiveResults.getString("name");
            BigDecimal price = archiveResults.getBigDecimal("sell_price");
            int inventory = archiveResults.getInt("inventory");
            products.add(new ProductDetails(id, name, price, inventory, true));
          }
          
          return products;
        }
      }
    
    } catch (Exception e) {
      // TODO: handle exception
    }
    // return products;
    return null;
  }

  /**
   * Retrieves a product's current basic details.
   *
   * @param productId identifier of the requested product
   * @return the product, or an empty result if it does not exist
   * @throws SQLException if the database query fails
   */
  public Optional<ProductDetails> findById(int productId) throws SQLException {
    String sql =
        """
        SELECT product_id, name, sell_price, inventory
        FROM ONLY public."Product"
        WHERE product_id = ?
        """;

    try (Connection connection = DatabaseConnection.open();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, productId);
      statement.setQueryTimeout(5);

      try (ResultSet results = statement.executeQuery()) {
        if (!results.next()) {
          return Optional.empty();
        }

        return Optional.of(
            new ProductDetails(
                results.getInt("product_id"),
                results.getString("name"),
                results.getBigDecimal("sell_price"),
                results.getInt("inventory"),
                false));
      }
    }
  }
	  /**
   * Updates basic product details and records the previous product state.
   *
   * @param request validated product changes
   * @return true if the product was updated, or false if it does not exist
   * @throws SQLException if saving or recording history fails
   */
  public boolean update(EditProductRequest request) throws SQLException {
    java.util.Objects.requireNonNull(request, "Edit request is required.");

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

        String historySql =
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
            connection.prepareStatement(historySql)) {
          statement.setInt(1, request.productId());
          statement.setQueryTimeout(5);

          if (statement.executeUpdate() != 1) {
            throw new SQLException("Could not record the product's previous state.");
          }
        }

        String updateSql =
            """
            UPDATE ONLY public."Product"
            SET name = ?, sell_price = ?, inventory = ?
            WHERE product_id = ?
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(updateSql)) {
          statement.setString(1, request.name());
          statement.setBigDecimal(2, request.sellPrice());
          statement.setInt(3, request.inventory());
          statement.setInt(4, request.productId());
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
   * Add a product details and records the previous product state.
   *
   * @param request validated product addition
   * @return true if the product was added, or false if it was not
   * @throws SQLException if adding or recording history fails
   */
  public boolean add(AddProductRequest request) throws SQLException {
    java.util.Objects.requireNonNull(request, "Add request is required.");

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

        int newId;
        boolean inUse = true;

        do{
          // Generate a new productId for the new product.
          newId = java.util.concurrent.ThreadLocalRandom.current()
              .nextInt(1, Integer.MAX_VALUE);

          // Check to see if a product with the ID exists.
          try (PreparedStatement statement =
              connection.prepareStatement(
                  """
                  SELECT product_id
                  FROM ONLY public."Product"
                  WHERE product_id = ?
                  FOR UPDATE
                  """)) {
            statement.setInt(1, newId);
            statement.setQueryTimeout(5);

            try (ResultSet result = statement.executeQuery()) {
              if (!result.next()) {
                inUse = false;
              }
            }
          }
        }while(inUse);

        // Lock the current product so its saved history matches the state
        // immediately before this update.
                String insertSql =
            """
            INSERT INTO public."Product"
                (product_id, name, sell_price, units_sold, inventory, parent_id)
            VALUES (?, ?, ?, 0, ?, NULL)
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(insertSql)) {
          statement.setInt(1, newId);
          statement.setString(2, request.name());
          statement.setBigDecimal(3, request.sellPrice());
          statement.setInt(4, request.inventory());
          statement.setQueryTimeout(5);

          if (statement.executeUpdate() != 1) {
            throw new SQLException("Could not add the product.");
          }
        }

        String historySql =
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
            connection.prepareStatement(historySql)) {
          statement.setInt(1, newId);
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

  /**
   * Archive a product and records the previous product state.
   *
   * @param request validated product archival
   * @return true if the product was archived, or false if it was not
   * @throws SQLException if archival or recording history fails
   */
  public boolean archive(ArchiveProductRequest request) throws SQLException {
    java.util.Objects.requireNonNull(request, "Archive request is required.");

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

        String historySql =
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
            connection.prepareStatement(historySql)) {
          statement.setInt(1, request.productId());
          statement.setQueryTimeout(5);

          if (statement.executeUpdate() != 1) {
            throw new SQLException("Could not record the product's previous state.");
          }
        }

        // String updateSql =
        //     """
        //     UPDATE ONLY public."Product"
        //     SET name = ?, sell_price = ?, inventory = ?
        //     WHERE product_id = ?
        //     """;

        String updateSql = 
            """
            DELETE FROM ONLY public."Product"
            WHERE product_id = ?
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(updateSql)) {
          statement.setInt(1, request.productId());
          statement.setQueryTimeout(5);

          if (statement.executeUpdate() != 1) {
            throw new SQLException("Could not delete the product.");
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
   * Delete a product and its previous product history.
   *
   * @param request validated product deletion
   * @return true if the product was deleted, or false if it was not
   * @throws SQLException if deleting or recording history fails
   */
  public boolean delete(DeleteProductRequest request) throws SQLException {
    java.util.Objects.requireNonNull(request, "Delete request is required.");

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

        // // Lock the current product so its saved history matches the state
        // // immediately before this update.
        // try (PreparedStatement statement =
        //     connection.prepareStatement(
        //         """
        //         SELECT product_id
        //         FROM ONLY public."Product"
        //         WHERE product_id = ?
        //         FOR UPDATE
        //         """)) {
        //   statement.setInt(1, request.productId());
        //   statement.setQueryTimeout(5);

        //   try (ResultSet result = statement.executeQuery()) {
        //     if (!result.next()) {
        //       connection.rollback();
        //       return false;
        //     }
        //   }
        // }

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
            if (result.next()) {
              String deleteSqlProduct = 
                  """
                  DELETE FROM ONLY public."Product"
                  WHERE product_id = ?
                  """;

              System.out.println(deleteSqlProduct);

              try (PreparedStatement deleteStatement =
                  connection.prepareStatement(deleteSqlProduct)) {
                deleteStatement.setInt(1, request.productId());

                System.out.println(deleteStatement);

                deleteStatement.setQueryTimeout(5);

                if (deleteStatement.executeUpdate() != 1) {
                  throw new SQLException("Could not delete the product.");
                }
              }
            }
          }
        }

        String deleteSqlProductHistory = 
            """
            DELETE FROM ONLY public."ProductHistory"
            WHERE product_id = ?
            """;

        System.out.println(deleteSqlProductHistory);

        try (PreparedStatement statement =
            connection.prepareStatement(deleteSqlProductHistory)) {
          statement.setInt(1, request.productId());

          statement.setQueryTimeout(5);

          statement.executeUpdate();

          // System.out.println(result);

          // if (result != 1 && result != 0) {
          //   throw new SQLException("Could not delete the product history.");
          // }
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
   * Duplicate a product, adding a copy of the product to the table.
   *
   * @param request validated product deletion
   * @return true if the product was deleted, or false if it was not
   * @throws SQLException if deleting or recording history fails
   */
  public boolean duplicate(DuplicateProductRequest request) throws SQLException {
    java.util.Objects.requireNonNull(request, "Duplicate request is required.");

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

        int newId;
        boolean inUse = true;

        do{
          // Generate a new productId for the new product.
          newId = java.util.concurrent.ThreadLocalRandom.current()
              .nextInt(1, Integer.MAX_VALUE);

          // Check to see if a product with the ID exists.
          try (PreparedStatement statement =
              connection.prepareStatement(
                  """
                  SELECT product_id
                  FROM ONLY public."Product"
                  WHERE product_id = ?
                  FOR UPDATE
                  """)) {
            statement.setInt(1, newId);
            statement.setQueryTimeout(5);

            try (ResultSet result = statement.executeQuery()) {
              if (!result.next()) {
                inUse = false;
              }
            }
          }
        }while(inUse);

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

        String updateSql = 
            """
            INSERT INTO public."Product"
              (product_id, name, sell_price, units_sold, inventory, parent_id)
            SELECT
              ?, p.name, p.sell_price, p.units_sold, p.inventory, p.parent_id
            FROM ONLY
              public."Product" as p
            WHERE
              p.product_id = ?;
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(updateSql)) {
          statement.setInt(1, newId);
          statement.setInt(2, request.productId());
          statement.setQueryTimeout(5);

          if (statement.executeUpdate() != 1) {
            throw new SQLException("Could not duplicate the product.");
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
