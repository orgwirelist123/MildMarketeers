package org.example.sales;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;

import org.junit.Test;

public class RegisterSaleRequestTest {
    @Test
    public void acceptsValidDetailsAndTrimsEmail() {
        RegisterSaleRequest request =
            new RegisterSaleRequest(1, 1, 12, new BigDecimal("2"), new BigDecimal("2"), 
                new BigDecimal("2"), 0, "    email@example.com    ");

        assertEquals(1, request.productId());
        assertEquals(1, request.soldQuantity());
        assertEquals(12, request.inventory());
        assertEquals(new BigDecimal("2"), request.originalUnitPrice());
        assertEquals(new BigDecimal("2"), request.sellPrice());
        assertEquals(new BigDecimal("2"), request.totalPrice());
        assertEquals(0, request.pastUnitsSold());
        assertEquals("email@example.com", request.email());
    }

    @Test
    public void acceptsValidDetailsWithoutEmail() {
        RegisterSaleRequest request =
            new RegisterSaleRequest(1, 1, 12, new BigDecimal("2"), new BigDecimal("2"), 
                new BigDecimal("2"), 0, null);

        assertEquals("", request.email());
    }

    @Test
    public void rejectsInvalidQuantityAndInventory() {
        assertInvalid(0, 12);
        assertInvalid(-2, 12);
        assertInvalid(13, 12);
    }

    private static void assertInvalid(int soldQuantity, int inventory) {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RegisterSaleRequest(1, soldQuantity, inventory, new BigDecimal("2"), new BigDecimal("2"), 
                new BigDecimal("2"), 0, "email@example.com"));
    }
}
