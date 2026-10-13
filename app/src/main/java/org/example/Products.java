package org.example;

// SQL packages
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.example.database.*;


public class Products
{
    /***
     * Queries the database's 'Product' table for names that contain the given string.
     * @param searchString
     * @return a result set of entries
     * @throws SQLException
     */
    public ResultSet search(String searchString) throws SQLException
    {
        Connection connection = DatabaseConnection.open();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT * FROM \"Product\" WHERE name LIKE \"%" + searchString + "%\"");
        
        // Close
        statement.close();
        connection.close();

        return result;
    }
}