package org.example.product;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Tests validation of edits before database changes are attempted. */
public class DuplicateProductRequestTest {

	@Test
	public void storesProductId() {
		int targetProductId = 29574953;
		DuplicateProductRequest request =
				new DuplicateProductRequest(targetProductId);

		assertEquals(targetProductId, request.productId());
	}
}
