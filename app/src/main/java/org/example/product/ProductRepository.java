package org.example.product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Optional;

import org.example.database.DatabaseConnection;

/** Reads and writes stored product information. */
public final class ProductRepository {

  /**
   * Retrieves the product details of all the products in the database.
   *
   * @return the list of products, or an empty result if they do not exist
   * @throws SQLException if the database query fails
   */
  public ArrayList<ProductDetails> searchProducts(String query) throws SQLException {
    String sql =
        """
        SELECT product_id, name, sell_price, inventory, units_sold, archived, parent_id
        FROM ONLY public."Product"
        WHERE name LIKE ?
        """;
    
    ArrayList<ProductDetails> products = new ArrayList<ProductDetails>();
    try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, "%" + query + "%");
      statement.setQueryTimeout(5);

      try (ResultSet results = statement.executeQuery()) {

        while(results.next())
        {
          int id = results.getInt("product_id");
          String name = results.getString("name");
          BigDecimal price = results.getBigDecimal("sell_price");
          int inventory = results.getInt("inventory");
          int unitsSold = results.getInt("units_sold");
          boolean archived = results.getBoolean("archived");
          int parentId = results.getInt("parent_id");
          products.add(
              new ProductDetails(id, name, price, inventory, unitsSold, archived, parentId));
        }

        return products;
      }

    } catch (Exception e) {
      // TODO: handle exception
    }
    return products;
  }

  /**
   * Retrieves the product details of all the products in the database.
   *
   * @return the product, or an empty result if it does not exist
   * @throws SQLException if the database query fails
   */
  public ArrayList<ProductDetails> retrieveProducts() throws SQLException {
    String sql =
        """
        SELECT product_id, name, sell_price, inventory, units_sold, archived, parent_id
        FROM ONLY public."Product"
        """;

    ArrayList<ProductDetails> products = new ArrayList<ProductDetails>();
    try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(5);

      try (ResultSet results = statement.executeQuery()) {

        while(results.next())
        {
          int id = results.getInt("product_id");
          String name = results.getString("name");
          BigDecimal price = results.getBigDecimal("sell_price");
          int inventory = results.getInt("inventory");
          int unitsSold = results.getInt("units_sold");
          boolean archived = results.getBoolean("archived");
          int parentId = results.getInt("parent_id");
          products.add(
              new ProductDetails(id, name, price, inventory, unitsSold, archived, parentId));
        }

        return products;
      }

    } catch (Exception e) {
      // TODO: handle exception
    }
    // return products;
    return null;
  }

  /**
   * Retrieves the product details of all the available parent products in the database.
   *
   * @return the list of products, or an empty result if they do not exist
   * @throws SQLException if the database query fails
   */
  public ArrayList<ProductDetails> retrieveAvailableParentProducts(int productId) throws SQLException {
    // Products must not be archived in order to add subproducts
    // We check if parent_id is null because a subproduct cannot be a parent
    String sql =
        """
        SELECT product_id, name, sell_price, inventory, units_sold, archived, parent_id
        FROM ONLY public."Product"
        WHERE parent_id IS NULL AND archived = FALSE AND product_id <> ?
        """;

    ArrayList<ProductDetails> products = new ArrayList<ProductDetails>();
    try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, productId);
      statement.setQueryTimeout(5);

      try (ResultSet results = statement.executeQuery()) {

        while(results.next())
        {
          int id = results.getInt("product_id");
          String name = results.getString("name");
          BigDecimal price = results.getBigDecimal("sell_price");
          int inventory = results.getInt("inventory");
          int unitsSold = results.getInt("units_sold");
          boolean archived = results.getBoolean("archived");
          int parentId = results.getInt("parent_id");
          products.add(
              new ProductDetails(id, name, price, inventory, unitsSold, archived, parentId));
        }

        return products;
      }

    } catch (Exception e) {
      // TODO: handle exception
    }
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
        SELECT product_id, name, sell_price, inventory, units_sold, archived, parent_id
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
                results.getInt("units_sold"),
                results.getBoolean("archived"),
                results.getInt("parent_id")));
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
    return update(request, null);
  }

  /** Saves product fields and optional tag assignments in the same transaction. */
  public boolean update(EditProductRequest request, java.util.List<String> tags)
      throws SQLException {
    return update(request, tags, null);
  }

  public boolean update(EditProductRequest request, java.util.List<String> tags,
      java.util.Map<String, String> tagColors) throws SQLException {
    tags = org.example.tags.TagRepository.validate(tags);
    tagColors = org.example.tags.TagRepository.validateColors(tags, tagColors);
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
                SELECT product_id, parent_id
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
            if (result.getInt("parent_id") != request.parentId()) {
              requireActiveParent(connection, request.parentId(), request.productId());
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
            SET name = ?, sell_price = ?, inventory = ?, parent_id = ?
            WHERE product_id = ?
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(updateSql)) {
          statement.setString(1, request.name());
          statement.setBigDecimal(2, request.sellPrice());
          statement.setInt(3, request.inventory());

          if (request.parentId() == 0) {
            statement.setNull(4, Types.INTEGER);
          } else {
            statement.setInt(4, request.parentId());
          }

          statement.setInt(5, request.productId());

          statement.setQueryTimeout(5);

          if (statement.executeUpdate() != 1) {
            throw new SQLException("Could not update the product.");
          }
        }

        org.example.tags.TagRepository.replace(connection, request.productId(), tags, tagColors);
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
   * @return product identifier, if the product was added, or 0 if it was not
   * @throws SQLException if adding or recording history fails
   */
  public int add(AddProductRequest request) throws SQLException {
    return add(request, null);
  }

  /** Saves product fields and optional tag assignments in the same transaction. */
  public int add(AddProductRequest request, java.util.List<String> tags)
      throws SQLException {
    return add(request, tags, null);
  }

  public int add(AddProductRequest request, java.util.List<String> tags,
      java.util.Map<String, String> tagColors) throws SQLException {
    tags = org.example.tags.TagRepository.validate(tags);
    tagColors = org.example.tags.TagRepository.validateColors(tags, tagColors);
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

        requireActiveParent(connection, request.parentId(), 0);

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
            VALUES (?, ?, ?, 0, ?, ?)
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(insertSql)) {
          statement.setInt(1, newId);
          statement.setString(2, request.name());
          statement.setBigDecimal(3, request.sellPrice());
          statement.setInt(4, request.inventory());

          if (request.parentId() == 0) {
            statement.setNull(5, Types.INTEGER);
          } else {
            statement.setInt(5, request.parentId());
          }

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
        org.example.tags.TagRepository.replace(connection, newId, tags, tagColors);
        connection.commit();
        return newId;
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
                 parent_id, archived, change_id, change_date, change_time)
            SELECT product_id, name, sell_price, units_sold, inventory,
                   parent_id, archived,
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
            UPDATE ONLY public."Product"
            SET archived = ?
            WHERE product_id = ? OR parent_id = ?;
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(updateSql)) {
          statement.setBoolean(1, true);
          statement.setInt(2, request.productId());
          statement.setInt(3, request.productId());
          statement.setQueryTimeout(5);

          if (statement.executeUpdate() == 0) {
            throw new SQLException("Could not archive the product.");
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


  /** Restores only the selected product, preserving related records and recording history. */
  public boolean restore(ArchiveProductRequest request) throws SQLException {
    java.util.Objects.requireNonNull(request, "Restore request is required.");

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
                SELECT product_id, parent_id, archived
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
            if (!result.getBoolean("archived")) {
              connection.commit();
              return true;
            }
            requireActiveParent(connection, result.getInt("parent_id"), request.productId());
          }
        }

        String historySql =
            """
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
            SET archived = ?
            WHERE product_id = ?;
            """;

        try (PreparedStatement statement =
            connection.prepareStatement(updateSql)) {
          statement.setBoolean(1, false);
          statement.setInt(2, request.productId());
          statement.setQueryTimeout(5);

          if (statement.executeUpdate() == 0) {
            throw new SQLException("Could not restore the product.");
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
        // Match the lock order used by product edits and sales.
        try (PreparedStatement statement = connection.prepareStatement(
            "LOCK TABLE public.\"ProductHistory\" IN EXCLUSIVE MODE")) {
          statement.setQueryTimeout(5);
          statement.execute();
        }

        try (PreparedStatement statement = connection.prepareStatement(
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

        // Include inherited SaleHistory rows to protect historical sales too.
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT EXISTS (
              SELECT 1 FROM public."Sale" WHERE product_id = ?
            )
            """)) {
          statement.setInt(1, request.productId());
          statement.setQueryTimeout(5);
          try (ResultSet result = statement.executeQuery()) {
            result.next();
            if (result.getBoolean(1)) {
              throw new IllegalArgumentException(
                  "This product has recorded sales. Archive it instead.");
            }
          }
        }

        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT EXISTS (
              SELECT 1 FROM ONLY public."Product" WHERE parent_id = ?
            )
            """)) {
          statement.setInt(1, request.productId());
          statement.setQueryTimeout(5);
          try (ResultSet result = statement.executeQuery()) {
            result.next();
            if (result.getBoolean(1)) {
              throw new IllegalArgumentException(
                  "This product has subproducts. Archive it instead.");
            }
          }
        }

        // Remove current dependencies, preserving shared tags and history.
        for (String sql : new String[] {
            "DELETE FROM ONLY public.\"Material\" WHERE product_id = ?",
            "DELETE FROM ONLY public.\"TagFor\" WHERE product_id = ?"
        }) {
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, request.productId());
            statement.setQueryTimeout(5);
            statement.executeUpdate();
          }
        }

        try (PreparedStatement statement = connection.prepareStatement(
            """
            DELETE FROM ONLY public."Product"
            WHERE product_id = ?
            """)) {
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
                SELECT product_id, parent_id
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
            requireActiveParent(connection, result.getInt("parent_id"), 0);
          }
        }

        String updateSql =
            """
            INSERT INTO public."Product"
              (product_id, name, sell_price, units_sold, inventory, parent_id, archived)
            SELECT
              ?, p.name, p.sell_price, 0, p.inventory, p.parent_id, p.archived
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

  /** Validates a new parent assignment while holding its row lock until commit. */
  private static void requireActiveParent(Connection connection, int parentId, int productId)
      throws SQLException {
    if (parentId == 0) return;
    if (parentId == productId) {
      throw new IllegalArgumentException("A product cannot be its own parent.");
    }
    try (PreparedStatement statement = connection.prepareStatement("""
        SELECT archived, parent_id
        FROM ONLY public."Product"
        WHERE product_id = ?
        FOR UPDATE
        """)) {
      statement.setInt(1, parentId);
      statement.setQueryTimeout(5);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new IllegalArgumentException("The selected parent product no longer exists.");
        }
        if (result.getBoolean("archived")) {
          throw new IllegalArgumentException("Restore the parent product before adding or restoring a subproduct.");
        }
        if (result.getInt("parent_id") != 0) {
          throw new IllegalArgumentException("A subproduct cannot be selected as a parent.");
        }
      }
    }
    if (productId != 0) {
      try (PreparedStatement statement = connection.prepareStatement("""
          SELECT 1 FROM ONLY public."Product" WHERE parent_id = ? LIMIT 1
          """)) {
        statement.setInt(1, productId);
        statement.setQueryTimeout(5);
        try (ResultSet result = statement.executeQuery()) {
          if (result.next()) {
            throw new IllegalArgumentException("A product with subproducts cannot become a subproduct.");
          }
        }
      }
    }
  }
}
