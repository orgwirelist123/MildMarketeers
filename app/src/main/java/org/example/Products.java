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
     * @param searchString the string the search will look for in the product name
     * @return a result set of entries that contain the search string in it's name attribute
     * @throws SQLException
     */
    public ResultSet search(String searchString) throws SQLException
    {
        Connection connection = DatabaseConnection.open();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT * FROM \"Product\" WHERE name LIKE \"%" + searchString + "%\"");
        
        statement.close();
        connection.close();

        return result;
    }
}