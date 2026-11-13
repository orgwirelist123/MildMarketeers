package org.example.tags;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.example.database.DatabaseConnection;

public class TagRepository {
    
  /**
   * Retrieves all current tags of a product.
   * 
   * @param productId identifier of requested product
   * @return the product's tags, or an empty result if none exist
   * @throws SQLException if the database query fails
   */
  public ArrayList<String> findTagsByProductId(int productId) throws SQLException {
    String sql =
      """
      SELECT tag.label AS taglabel
      FROM public."TagFor" tagfor JOIN ONLY public."Tag" tag ON tagfor.tag_id = tag.tag_id
      WHERE tagfor.product_id = ?
      """;

    try (Connection connection = DatabaseConnection.open(); 
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, productId);
      statement.setQueryTimeout(5);
      
      try (ResultSet results = statement.executeQuery()) {   
        ArrayList<String> productTags = new ArrayList<String>();

        while(results.next())
        {
          productTags.add(results.getString("taglabel"));
        }
        
        return productTags;
      }    
    } catch (Exception e) {
        // TODO: handle exception
    }
    return null;
  }

//   public boolean addProductTags(int productId, String chosenTag) throws SQLException {
//     try (Connection connection = DatabaseConnection.open()) {
//         connection.setAutoCommit(false);

//         try {
//             // The current schema has no automatic history ID generator.
//             // Hold this lock until commit so concurrent history writes cannot
//             // allocate the same next ID.
//             try (PreparedStatement statement =
//                 connection.prepareStatement(
//                     "LOCK TABLE public.\"TagHistory\" IN EXCLUSIVE MODE")) {
//                 statement.setQueryTimeout(5);
//                 statement.execute();
//             }

//             int newId;
//             boolean inUse = true;

//             do {
//                 // Generate a new productId for the new product.
//                 newId = java.util.concurrent.ThreadLocalRandom.current()
//                     .nextInt(1, Integer.MAX_VALUE);

//                 // Check to see if a tag with the ID exists.
//                 try (PreparedStatement statement =
//                     connection.prepareStatement(
//                         """
//                         SELECT tag_id
//                         FROM ONLY public."Tag"
//                         WHERE tag_id = ?
//                         FOR UPDATE
//                         """)) {
//                     statement.setInt(1, newId);
//                     statement.setQueryTimeout(5);

//                     try (ResultSet result = statement.executeQuery()) {
//                         if (!result.next()) {
//                             inUse = false;
//                         }
//                     }
//                 }
//             } while(inUse);

//             String insertTagSql =
//             """
//             INSERT INTO public."Tag"(tag_id, label) 
//             SELECT ?, ?
//                 WHERE NOT EXISTS (
//                     SELECT 1 FROM public."Tag" WHERE label=?)
//             """;

//             try (PreparedStatement statement = connection.prepareStatement(insertTagSql)) {
//                 statement.setInt(1, newId);
//                 statement.setString(2, chosenTag);
//                 statement.setString(3, chosenTag);

//                 statement.setQueryTimeout(5);

//                 if (statement.executeUpdate() != 1) {
//                     throw new SQLException("Could not add the product.");
//                 }
//             }    

//             String insertTagForSql =
//             """
//             INSERT INTO public."TagFor"(tag_id, product_id)
//             VALUES (?, ?)
//             """;

//             try (PreparedStatement statement = connection.prepareStatement(insertTagSql)) {
//                 statement.setInt(1, newId);
//                 statement.setString(2, chosenTag);
//                 statement.setString(3, chosenTag);

//                 statement.setQueryTimeout(5);

//                 if (statement.executeUpdate() != 1) {
//                     throw new SQLException("Could not add the product.");
//                 }
//             }  

                
//         } catch (SQLException | RuntimeException exception) {
//             try {
//                 connection.rollback();
//             } catch (SQLException rollbackException) {
//                 exception.addSuppressed(rollbackException);
//             }
//             throw exception;
//         }
//     }
//   }

  // public boolean updateProductTags(int productId) throws SQLException {

  // }
}
