package org.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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

}
