package org.example;

// SQL packages
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.example.database.*;


public class Products
{

	// THIS FUNCTIONALITY WAS MOVED TO THE PRODUCT REPOSITORY 
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

	/***
	 * Queries the database's 'Product' table to sort the records based on product name (ascending), sell price (ascending),
	 * highest inventory size, and lowest inventory size. 
	 * Queries the database's 'ProductHistory' table to sort the records based on product added date (ascending).
	 * Queries the database's 'SaleHistory' table to sort the records based on product last sold date (ascending).
	 * @param sortBy the string to sort all products in the inventory
	 * @return a result set of entries sorted based on the sortBy param
	 * @throws SQLException
	 */
	public ResultSet sort(String sortBy) throws SQLException {
		Connection connection = DatabaseConnection.open();
		Statement statement = connection.createStatement();

		String orderBy = "";
		if(sortBy.equals("Name")) {
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
			return statement.executeQuery("SELECT DISTINCT ON (product_id) FROM \"ProductHistory\" ORDER BY change_date, change_time");
		}
		else if(sortBy.equals("LastSoldDate")) {
			return statement.executeQuery("SELECT DISTINCT ON (product_id) FROM \"SaleHistory\" ORDER BY change_date, change_time");
		}
		
		statement.close();
		connection.close();

		return statement.executeQuery("SELECT * FROM \"Product\" ORDER BY " + orderBy);
	}
}