package org.example.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import org.junit.Test;

/** Tests validation of edits before database changes are attempted. */
public class AddProductRequestTest {

  @Test
  public void acceptsValidDetailsAndTrimsName() {
    AddProductRequest request =
        new AddProductRequest("  Cookie  ", new BigDecimal("12"), 20);

    assertEquals("Cookie", request.name());
    assertEquals(new BigDecimal("12"), request.sellPrice());
    assertEquals(20, request.inventory());
  }

  @Test
  public void acceptsZeroPriceAndZeroInventory() {
    AddProductRequest request =
        new AddProductRequest("Sample", BigDecimal.ZERO, 0);

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
    AddProductRequest request =
        new AddProductRequest("a".repeat(50), BigDecimal.ONE, 1);

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
    AddProductRequest request =
        new AddProductRequest("Cookie", new BigDecimal("12.00"), 1);

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
        () -> new AddProductRequest(name, sellPrice, inventory));
  }
}
