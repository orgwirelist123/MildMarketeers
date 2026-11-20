package org.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Array;

import org.example.database.DatabaseConnection;
import org.junit.Test;

public class ProductsTest {
	@Test
	public void productsHasAProduct() throws SQLException {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		Products classUnderTest = new Products();

		Connection connection = DatabaseConnection.open();
		PreparedStatement prepStatement = connection
				.prepareStatement("INSERT into \"Products\" values(1, Product1, 5, 0, 0, NULL)");

		prepStatement.execute();

		prepStatement.close();
		connection.close();

		assertEquals(1, classUnderTest.search("Product1").getFetchSize());
	}

	@Test
	// Tests sort by product name alphabetically (asc)
	public void productsSortedByName() throws SQLException {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		Products classUnderTest = new Products();

		Connection connection = DatabaseConnection.open();

		String[] testNames = {"B", "C", "A", "D"};

		for(int i = 0; i < testNames.length; i++) {
			PreparedStatement prepStatement = connection
				.prepareStatement("INSERT into \"Product\" values(" + i + ", Product" + testNames[i] + ", 5, 0, 0, NULL)");

			prepStatement.execute();
			prepStatement.close();
		}
		
		connection.close();

		Array sortedProducts = classUnderTest.sort("Name").getArray("name");
		String[] productsArray = (String[])sortedProducts.getArray();

		assertEquals("ProductA", productsArray[0]);
		assertEquals("ProductB", productsArray[1]);
		assertEquals("ProductC", productsArray[2]);
		assertEquals("ProductD", productsArray[3]);
	}

	@Test
	// Tests sort by product sell price (asc)
	public void productsSortedByPrice() throws SQLException {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		Products classUnderTest = new Products();

		Connection connection = DatabaseConnection.open();

		String[] testPrices = {"12", "20", "5", "32"};

		for(int i = 0; i < testPrices.length; i++) {
			PreparedStatement prepStatement = connection
				.prepareStatement("INSERT into \"Product\" values(" + i + ", Product, " + testPrices[i] + ", 0, 0, NULL)");

			prepStatement.execute();
			prepStatement.close();
		}
		
		connection.close();

		Array sortedProducts = classUnderTest.sort("Price").getArray("sell_price");
		String[] productsArray = (String[])sortedProducts.getArray();

		assertEquals("5", productsArray[0]);
		assertEquals("12", productsArray[1]);
		assertEquals("20", productsArray[2]);
		assertEquals("32", productsArray[3]);
	}

	@Test
	// Tests sort by highest product inventory size
	public void productsSortedByHighestInventorySize() throws SQLException {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		Products classUnderTest = new Products();

		Connection connection = DatabaseConnection.open();

		String[] testSizes = {"1", "0", "5", "2"};

		for(int i = 0; i < testSizes.length; i++) {
			PreparedStatement prepStatement = connection
				.prepareStatement("INSERT into \"Product\" values(" + i + ", Product, 5, 0, " + testSizes[i] + ", NULL)");

			prepStatement.execute();
			prepStatement.close();
		}
		
		connection.close();

		Array sortedProducts = classUnderTest.sort("HighestInventory").getArray("inventory");
		String[] productsArray = (String[])sortedProducts.getArray();

		assertEquals("5", productsArray[0]);
		assertEquals("2", productsArray[1]);
		assertEquals("1", productsArray[2]);
		assertEquals("0", productsArray[3]);
	}

	@Test
	// Tests sort by lowest product inventory size
	public void productsSortedByLowestInventorySize() throws SQLException {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		Products classUnderTest = new Products();

		Connection connection = DatabaseConnection.open();

		String[] testSizes = {"1", "0", "5", "2"};

		for(int i = 0; i < testSizes.length; i++) {
			PreparedStatement prepStatement = connection
				.prepareStatement("INSERT into \"Product\" values(" + i + ", Product, 5, 0, " + testSizes[i] + ", NULL)");

			prepStatement.execute();
			prepStatement.close();
		}
		
		connection.close();

		Array sortedProducts = classUnderTest.sort("LowestInventory").getArray("inventory");
		String[] productsArray = (String[])sortedProducts.getArray();

		assertEquals("0", productsArray[0]);
		assertEquals("1", productsArray[1]);
		assertEquals("2", productsArray[2]);
		assertEquals("5", productsArray[3]);
	}

	@Test
	// Tests sort by product added date (asc)
	public void productsSortedByAddedDate() throws SQLException {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		Products classUnderTest = new Products();

		Connection connection = DatabaseConnection.open();

		String[] testDates = {"1/1/2026", "1/1/2026", "1/5/2013", "1/2/2026"};
		String[] testTimes = {"04:06:18", "03:06:21", "06:32:12", "10:12:30"};

		for(int i = 0; i < testDates.length; i++) {
			PreparedStatement prepStatement = connection
				.prepareStatement("INSERT into \"ProductHistory\" values(" + i + ", Product " + i + ", 5, 0, 0, NULL, " + 
				i + ", " + testDates[i] + ", " + testTimes[i] + ")");

			prepStatement.execute();
			prepStatement.close();
		}
		
		connection.close();

		Array sortedProducts = classUnderTest.sort("AddDate").getArray("name");
		String[] productsArray = (String[])sortedProducts.getArray();

		assertEquals("Product2", productsArray[0]);
		assertEquals("Product1", productsArray[1]);
		assertEquals("Product0", productsArray[2]);
		assertEquals("Product3", productsArray[3]);
	}

	@Test
	// Tests sort by product last sold date (asc)
	public void productsSortedBySoldDate() throws SQLException {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		Products classUnderTest = new Products();

		Connection connection = DatabaseConnection.open();

		String[] testDates = {"1/1/2026", "1/1/2026", "1/5/2013", "1/2/2026"};
		String[] testTimes = {"04:06:18", "03:06:21", "06:32:12", "10:12:30"};

		for(int i = 0; i < testDates.length; i++) {
			PreparedStatement prepStatement = connection
				.prepareStatement("INSERT into \"SaleHistory\" values(" + i + ", Product " + i + ", " + testDates[i] + ", " + testTimes[i] + ", " + 
				"0, test@email, " + i + ", " + testDates[i] + ", " + testTimes[i] + ")");

			prepStatement.execute();
			prepStatement.close();
		}
		
		connection.close();

		Array sortedProducts = classUnderTest.sort("LastSoldDate").getArray("name");
		String[] productsArray = (String[])sortedProducts.getArray();

		assertEquals("Product2", productsArray[0]);
		assertEquals("Product1", productsArray[1]);
		assertEquals("Product0", productsArray[2]);
		assertEquals("Product3", productsArray[3]);
	}
}
