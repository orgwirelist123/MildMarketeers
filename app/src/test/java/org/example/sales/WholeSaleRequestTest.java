package org.example.sales;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import org.junit.Test;

/** Checks whole-sale discounts and monetary input boundaries. */
public class WholeSaleRequestTest {

  @Test
  public void subtractsDiscountOnceForTheWholeSale() {
    WholeSaleRequest request = request(2, "10.00", "3.00");
    assertEquals(new BigDecimal("17.00"), request.total());
  }

  @Test
  public void preservesDiscountThatCannotBeDividedEvenlyAcrossUnits() {
    WholeSaleRequest request = request(3, "10.00", "1.00");
    assertEquals(new BigDecimal("29.00"), request.total());
  }

  @Test
  public void acceptsDecimalPricesAboveNinetyNine() {
    assertEquals(
        new BigDecimal("315.96"),
        request(2, "157.98", "0").total());
  }

  @Test
  public void acceptsFullDiscountAndZeroPrice() {
    assertEquals(new BigDecimal("0.00"), request(2, "10", "20").total());
    assertEquals(new BigDecimal("0.00"), request(1, "0", "0").total());
  }

  @Test
  public void acceptsMaximumMoneyAndTrailingZeros() {
    assertEquals(
        new BigDecimal("9999999999.99"),
        request(1, "9999999999.99", "0").total());
    assertEquals(
        new BigDecimal("0.00"),
        request(1, "9999999999.99", "9999999999.99").total());
    assertEquals(new BigDecimal("10.00"), request(1, "10.000", "0").total());
  }

  @Test
  public void rejectsInvalidQuantities() {
    assertThrows(IllegalArgumentException.class, () -> request(0, "10", "0"));
    assertThrows(IllegalArgumentException.class, () -> request(-1, "10", "0"));
  }

  @Test
  public void rejectsInvalidUnitPrices() {
    for (String value : new String[] {"-0.01", "1.001", "10000000000"}) {
      assertThrows(
          IllegalArgumentException.class, () -> request(1, value, "0"));
    }
  }

  @Test
  public void rejectsInvalidDiscounts() {
    for (String value : new String[] {"-0.01", "1.001", "10000000000"}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> request(2, "9999999999.99", value));
    }
    assertThrows(
        IllegalArgumentException.class, () -> request(2, "10", "20.01"));
  }

  @Test
  public void rejectsMissingMoney() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new WholeSaleRequest(1, 1, null, BigDecimal.ZERO, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WholeSaleRequest(1, 1, BigDecimal.ONE, null, null));
  }

  @Test
  public void normalizesEmailAndRejectsExcessiveLength() {
    assertEquals("", request(1, "10", "0").email());
    assertEquals(
        "email@example.com",
        new WholeSaleRequest(
            1, 1, BigDecimal.TEN, BigDecimal.ZERO, "  email@example.com  ").email());
    assertThrows(
        IllegalArgumentException.class,
        () -> new WholeSaleRequest(
            1, 1, BigDecimal.TEN, BigDecimal.ZERO, "a".repeat(257)));
  }

  private static WholeSaleRequest request(
      int quantity, String unitPrice, String discount) {
    return new WholeSaleRequest(
        1, quantity, new BigDecimal(unitPrice), new BigDecimal(discount), null);
  }
}
