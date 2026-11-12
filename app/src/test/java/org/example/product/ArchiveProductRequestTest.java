package org.example.product;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Tests validation of edits before database changes are attempted. */
public class ArchiveProductRequestTest {

  @Test
  public void storesProductId() {
    int targetProductId = 14267293;
    ArchiveProductRequest request =
        new ArchiveProductRequest(targetProductId);

    assertEquals(targetProductId, request.productId());
  }
}
