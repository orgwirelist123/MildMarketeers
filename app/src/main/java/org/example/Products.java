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
        
        statement.close();
        connection.close();

        return result;
    }

    public ResultSet sort(String sortBy) throws SQLException {
        Connection connection = DatabaseConnection.open();
        Statement statement = connection.createStatement();

        String orderBy = "";
        ResultSet result;
        if(sortBy.equals("Alphabetically")) {
            orderBy = "name";
        }
        else if(sortBy.equals("Price")) {
            orderBy = "sell_price";
        }
        else if(sortBy.equals("HighestInventory")) {
            orderBy = "inventory ASC";
        }
        else if(sortBy.equals("LowestInventory")) {
            orderBy = "inventory";
        }
        else if(sortBy.equals("AddDate")) {
            // query = select distinct on (product_id) from "ProductHistory" order by change_date, change_time
            result = statement.executeQuery("SELECT DISTINCT ON (product_id) FROM \"ProductHistory\" ORDER BY change_date, change_time");
        }
        else if(sortBy.equals("LastSoldDate")) {
            // query = select distinct on (product_id) from "SaleHistory" order by change_date, change_time
            result = statement.executeQuery("SELECT DISTINCT ON (product_id) FROM \"SaleHistory\" ORDER BY change_date, change_time");

        }

        result = statement.executeQuery("SELECT * FROM \"Product\" ORDER BY " + orderBy);

        statement.close();
        connection.close();

        return result;
    }
}