package org.example.product;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Tests validation of edits before database changes are attempted. */
public class DeleteProductRequestTest {

	@Test
	public void storesProductId() {
		int targetProductId = 64397984;
		DeleteProductRequest request =
				new DeleteProductRequest(targetProductId);

		assertEquals(targetProductId, request.productId());
	}
}
