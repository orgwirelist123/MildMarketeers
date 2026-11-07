package org.example.sales;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;

import org.junit.Test;

public class RegisterSaleRequestTest {
    @Test
    public void acceptsValidDetailsAndTrimsEmail() {
        RegisterSaleRequest request =
            new RegisterSaleRequest(1, 1, 12, 4, new BigDecimal("50"), "    email@example.com    ");

        assertEquals(1, request.productId());
        assertEquals(1, request.quantity());
        assertEquals(12, request.inventory());
        assertEquals(4, request.unitsSold());
        assertEquals(new BigDecimal("50"), request.discount());
        assertEquals("email@example.com", request.email());
    }

    @Test
    public void acceptsValidDetailsWithoutEmailAndDiscount() {
        RegisterSaleRequest request =
            new RegisterSaleRequest(1, 1, 12, 4, null, null);

        assertEquals(BigDecimal.ZERO, request.discount());
        assertEquals("", request.email());
    }

    @Test
    public void rejectsInvalidQuantityAndInventory() {
        assertInvalid(0, 12, BigDecimal.ZERO);
        assertInvalid(-2, 12, BigDecimal.ZERO);
        assertInvalid(13, 12, BigDecimal.ZERO);
    }

    @Test
    public void rejectsDiscountsThatCurrentSchemaCannotPreserve() {
        assertInvalid(1, 12, new BigDecimal("12.50"));
        assertInvalid(1, 12, new BigDecimal("100"));
    }

    @Test
    public void acceptsWholePriceWithDecimalNotation() {
        RegisterSaleRequest request =
            new RegisterSaleRequest(1, 1, 12, 4, new BigDecimal("12.00"), null);

        assertEquals(0, request.discount().compareTo(new BigDecimal("12")));
    }

    private static void assertInvalid(int quantity, int inventory, BigDecimal discount) {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RegisterSaleRequest(1, quantity, inventory, 4, discount, "email@example.com"));
    }
}
