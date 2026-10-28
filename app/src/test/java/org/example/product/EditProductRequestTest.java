package org.example.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import org.junit.Test;

/** Tests validation of edits before database changes are attempted. */
public class EditProductRequestTest {

  @Test
  public void acceptsValidDetailsAndTrimsName() {
    EditProductRequest request =
        new EditProductRequest(1, "  Cookie  ", new BigDecimal("12"), 20);

    assertEquals(1, request.productId());
    assertEquals("Cookie", request.name());
    assertEquals(new BigDecimal("12"), request.sellPrice());
    assertEquals(20, request.inventory());
  }

  @Test
  public void acceptsZeroPriceAndZeroInventory() {
    EditProductRequest request =
        new EditProductRequest(1, "Sample", BigDecimal.ZERO, 0);

    assertEquals(BigDecimal.ZERO, request.sellPrice());
    assertEquals(0, request.inventory());
  }

  @Test
  public void rejectsMissingOrBlankName() {
    assertInvalid(null, BigDecimal.ONE, 1);
    assertInvalid("   ", BigDecimal.ONE, 1);
  }

  @Test
  public void rejectsNameLongerThanDatabaseLimit() {
    assertInvalid("a".repeat(51), BigDecimal.ONE, 1);
  }

  @Test
  public void acceptsNameAtDatabaseLimit() {
    EditProductRequest request =
        new EditProductRequest(1, "a".repeat(50), BigDecimal.ONE, 1);

    assertEquals(50, request.name().length());
  }

  @Test
  public void rejectsMissingOrNegativePrice() {
    assertInvalid("Cookie", null, 1);
    assertInvalid("Cookie", new BigDecimal("-1"), 1);
  }

  @Test
  public void rejectsPricesThatCurrentSchemaCannotPreserve() {
    assertInvalid("Cookie", new BigDecimal("12.50"), 1);
    assertInvalid("Cookie", new BigDecimal("100"), 1);
  }

  @Test
  public void acceptsWholePriceWithDecimalNotation() {
    EditProductRequest request =
        new EditProductRequest(1, "Cookie", new BigDecimal("12.00"), 1);

    assertEquals(0, request.sellPrice().compareTo(new BigDecimal("12")));
  }

  @Test
  public void rejectsNegativeInventory() {
    assertInvalid("Cookie", BigDecimal.ONE, -1);
  }

  private static void assertInvalid(
      String name, BigDecimal sellPrice, int inventory) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EditProductRequest(1, name, sellPrice, inventory));
  }
}
