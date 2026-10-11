package org.example.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Opens PostgreSQL connections using environment variables. */
public final class DatabaseConnection {

  private DatabaseConnection() {}

  /**
   * Opens a new database connection.
   *
   * @return a connection that the caller must close
   * @throws SQLException if the database connection fails
   * @throws IllegalStateException if required configuration is missing
   */
  public static Connection open() throws SQLException {
    String url = requireEnvironmentVariable("MM_DB_URL");
    String user = requireEnvironmentVariable("MM_DB_USER");
    String password = requireEnvironmentVariable("MM_DB_PASSWORD");

    Properties properties = new Properties();
    properties.setProperty("user", user);
    properties.setProperty("password", password);
    properties.setProperty("connectTimeout", "5");

    return DriverManager.getConnection(url, properties);
  }

  private static String requireEnvironmentVariable(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Missing required environment variable: " + name);
    }
    return value;
  }
}
