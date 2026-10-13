package org.example;

// SQL packages
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.example.database.*;


public class Products
{
    public ResultSet search(String searchString) throws SQLException
    {
        Connection connection = DatabaseConnection.open();
        Statement statement = connection.createStatement();

        return statement.executeQuery("SELECT * FROM \"Product\" WHERE name LIKE \"%" + searchString + "%\"");
    }
}