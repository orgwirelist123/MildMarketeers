package org.example.product;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.UUID;
import org.example.database.DatabaseConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Database checks for restore and archived-parent validation. */
public class ProductRestoreRepositoryTest {
	private final ProductRepository repository = new ProductRepository();
	private final ArrayList<Integer> ids = new ArrayList<>();
	private Connection connection;
	private int parentId;
	private int childId;

	@Before
	public void setUp() throws Exception {
		assumeTrue("Set MM_DB_TEST=true to run database tests.",
				"true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
		connection = DatabaseConnection.open();
		parentId = add(0);
		childId = add(parentId);
	}

	private int add(int parent) throws Exception {
		int id = repository.add(new AddProductRequest(
				"Restore QA " + UUID.randomUUID(), new BigDecimal("12.00"), 5, parent));
		ids.add(id);
		return id;
	}

	@After
	public void tearDown() throws Exception {
		if (connection == null) return;
		try {
			connection.setAutoCommit(false);
			for (int i = ids.size() - 1; i >= 0; i--) {
				for (String table : new String[] {"ProductHistory", "Product"}) {
					try (PreparedStatement statement = connection.prepareStatement(
							"DELETE FROM ONLY public.\"" + table + "\" WHERE product_id = ?")) {
						statement.setInt(1, ids.get(i));
						statement.executeUpdate();
					}
				}
			}
			connection.commit();
		} finally {
			connection.close();
		}
	}

	@Test
	public void restoresParentThenChildWithoutChangingStockOrPrice() throws Exception {
		repository.archive(new ArchiveProductRequest(parentId));
		assertTrue(repository.findById(childId).orElseThrow().archived());
		assertTrue(repository.restore(new ArchiveProductRequest(parentId)));
		assertTrue(repository.findById(childId).orElseThrow().archived());
		assertTrue(repository.restore(new ArchiveProductRequest(childId)));
		ProductDetails child = repository.findById(childId).orElseThrow();
		assertFalse(child.archived());
		assertEquals(5, child.inventory());
		assertEquals(0, child.unitsSold());
		assertEquals(new BigDecimal("12.00"), child.sellPrice());
		assertEquals(parentId, child.parentId());
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT archived FROM ONLY public."ProductHistory"
				WHERE product_id = ? ORDER BY change_id DESC LIMIT 1
				""")) {
			statement.setInt(1, childId);
			try (var result = statement.executeQuery()) {
				assertTrue(result.next());
				assertTrue(result.getBoolean("archived"));
			}
		}
		int history = historyCount(childId);
		assertTrue(repository.restore(new ArchiveProductRequest(childId)));
		assertEquals(history, historyCount(childId));
	}

	@Test
	public void cannotRestoreChildWhileParentIsArchived() throws Exception {
		repository.archive(new ArchiveProductRequest(parentId));
		int history = historyCount(childId);
		assertThrows(IllegalArgumentException.class,
				() -> repository.restore(new ArchiveProductRequest(childId)));
		assertTrue(repository.findById(childId).orElseThrow().archived());
		assertEquals(history, historyCount(childId));
	}

	@Test
	public void cannotAddOrDuplicateChildOfArchivedParent() throws Exception {
		repository.archive(new ArchiveProductRequest(parentId));
		int before = childCount();
		assertThrows(IllegalArgumentException.class, () -> repository.add(
				new AddProductRequest("Rejected restore QA", BigDecimal.ONE, 1, parentId)));
		assertThrows(IllegalArgumentException.class,
				() -> repository.duplicate(new DuplicateProductRequest(childId)));
		assertEquals(before, childCount());
	}

	@Test
	public void cannotMoveProductUnderArchivedParent() throws Exception {
		int standalone = add(0);
		ProductDetails before = repository.findById(standalone).orElseThrow();
		int history = historyCount(standalone);
		repository.archive(new ArchiveProductRequest(parentId));
		assertThrows(IllegalArgumentException.class, () -> repository.update(
				new EditProductRequest(standalone, before.name(), BigDecimal.ONE, 99, parentId)));
		assertEquals(before, repository.findById(standalone).orElseThrow());
		assertEquals(history, historyCount(standalone));
	}

	@Test
	public void missingProductReturnsFalse() throws Exception {
		repository.delete(new DeleteProductRequest(childId));
		assertFalse(repository.restore(new ArchiveProductRequest(childId)));
	}

	private int historyCount(int id) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT COUNT(*) FROM ONLY public."ProductHistory" WHERE product_id = ?
				""")) {
			statement.setInt(1, id);
			try (var result = statement.executeQuery()) {
				result.next();
				return result.getInt(1);
			}
		}
	}

	private int childCount() throws Exception {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT COUNT(*) FROM ONLY public."Product" WHERE parent_id = ?
				""")) {
			statement.setInt(1, parentId);
			try (var result = statement.executeQuery()) {
				result.next();
				return result.getInt(1);
			}
		}
	}
}
