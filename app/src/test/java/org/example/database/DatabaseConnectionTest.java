package org.example.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.Test;

/** Tests the connection to a locally configured PostgreSQL database. */
public class DatabaseConnectionTest {

	@Test
	public void connectsAndExecutesQuery() throws Exception {
		assumeTrue(
				"Set MM_DB_TEST=true to run the database integration test.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));

		try (Connection connection = DatabaseConnection.open();
				Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(5);
			try (ResultSet resultSet = statement.executeQuery("SELECT * FROM \"Product\" LIMIT 1")) {
								assertTrue(resultSet != null);
			}

			try (ResultSet result = statement.executeQuery("SELECT 1")) {
				assertTrue(result.next());
				assertEquals(1, result.getInt(1));
			}
		}
	}
}
