package org.example.product;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import org.junit.Test;

/** Tests validation of edits before database changes are attempted. */
public class AddProductRequestTest {

  @Test
  public void acceptsValidDetailsAndTrimsName() {
    AddProductRequest request =
        new AddProductRequest("  Cookie  ", new BigDecimal("12"), 20, 0);

    assertEquals("Cookie", request.name());
    assertEquals(new BigDecimal("12"), request.sellPrice());
    assertEquals(20, request.inventory());
  }

  @Test
  public void acceptsZeroPriceAndZeroInventory() {
    AddProductRequest request =
        new AddProductRequest("Sample", BigDecimal.ZERO, 0, 0);

    assertEquals(BigDecimal.ZERO, request.sellPrice());
    assertEquals(0, request.inventory());
  }

  @Test
  public void rejectsMissingOrBlankName() {
    assertInvalid(null, BigDecimal.ONE, 1, 0);
    assertInvalid("   ", BigDecimal.ONE, 1, 0);
  }

  @Test
  public void rejectsNameLongerThanDatabaseLimit() {
    assertInvalid("a".repeat(51), BigDecimal.ONE, 1, 0);
  }

  @Test
  public void acceptsNameAtDatabaseLimit() {
    AddProductRequest request =
        new AddProductRequest("a".repeat(50), BigDecimal.ONE, 1, 0);

    assertEquals(50, request.name().length());
  }

  @Test
  public void rejectsMissingOrNegativePrice() {
    assertInvalid("Cookie", null, 1, 0);
    assertInvalid("Cookie", new BigDecimal("-1"), 1, 0);
  }

    @Test
  public void acceptsDecimalAndLargerPrices() {
    for (String value : new String[] {"12.50", "100", "157.98", "12.500"}) {
      BigDecimal price = new BigDecimal(value);
      AddProductRequest request =
          new AddProductRequest("Cookie", price, 1, 0);

      assertEquals(0, price.compareTo(request.sellPrice()));
    }
  }

  @Test
  public void acceptsMaximumPrice() {
    BigDecimal price = new BigDecimal("9999999999.99");
    AddProductRequest request =
        new AddProductRequest("Cookie", price, 1, 0);

    assertEquals(0, price.compareTo(request.sellPrice()));
  }

  @Test
  public void rejectsPricesOutsideSupportedRangeOrPrecision() {
    assertInvalid("Cookie", new BigDecimal("-0.01"), 1, 0);
    assertInvalid("Cookie", new BigDecimal("12.501"), 1, 0);
    assertInvalid("Cookie", new BigDecimal("10000000000.00"), 1, 0);
  }

  @Test
  public void acceptsWholePriceWithDecimalNotation() {
    AddProductRequest request =
        new AddProductRequest("Cookie", new BigDecimal("12.00"), 1, 0);

    assertEquals(0, request.sellPrice().compareTo(new BigDecimal("12")));
  }

  @Test
  public void rejectsNegativeInventory() {
    assertInvalid("Cookie", BigDecimal.ONE, -1, 0);
  }

  @Test
  public void rejectsInvalidParentId() {
    assertInvalid("Cookie", BigDecimal.ONE, 1, -1);
  }

  private static void assertInvalid(
      String name, BigDecimal sellPrice, int inventory, int parentId) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AddProductRequest(name, sellPrice, inventory, parentId));
  }
}
