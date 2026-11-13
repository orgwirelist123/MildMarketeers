package org.example.product;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.example.database.DatabaseConnection;
import org.example.tags.TagRepository;
import org.junit.*;

/** Database coverage for product tag persistence and shared-tag deletion. */
public class ProductTagsRepositoryTest {
  private final ProductRepository repository = new ProductRepository();
  private final TagRepository tags = new TagRepository();
  private final List<Integer> ids = new ArrayList<>();
  private final String label = "QA-" + UUID.randomUUID();
  private Connection connection;

  @Before public void setUp() throws Exception {
    assumeTrue("Set MM_DB_TEST=true to run database tests.",
        "true".equalsIgnoreCase(System.getenv("MM_DB_TEST")));
    connection = DatabaseConnection.open();
  }

  private int add(List<String> labels) throws Exception {
    int id = repository.add(new AddProductRequest(
        "Tag QA " + UUID.randomUUID(), new BigDecimal("5.00"), 2, 0), labels);
    ids.add(id);
    return id;
  }

  private List<String> assigned(int id) throws Exception {
    return tags.catalog().products().getOrDefault(id, List.of());
  }

  @Test public void createsReusesAndRemovesTags() throws Exception {
    int first = add(List.of(" " + label + " ", label.toLowerCase(Locale.ROOT)));
    int second = add(List.of(label.toLowerCase(Locale.ROOT)));
    assertEquals(List.of(label), assigned(first));
    assertEquals(List.of(label), assigned(second));
    assertTrue(repository.update(new EditProductRequest(
        first, "Updated tag QA", new BigDecimal("6.00"), 3, 0), List.of()));
    assertTrue(assigned(first).isEmpty());
    assertEquals(List.of(label), assigned(second));
  }

  @Test public void legacyEditPreservesTags() throws Exception {
    int id = add(List.of(label));
    assertTrue(repository.update(new EditProductRequest(
        id, "Legacy edit QA", new BigDecimal("7.00"), 4, 0)));
    assertEquals(List.of(label), assigned(id));
  }

  @Test public void invalidTagsDoNotChangeProductOrAssignments() throws Exception {
    int id = add(List.of(label));
    var before = repository.findById(id).orElseThrow();
    assertThrows(IllegalArgumentException.class, () -> repository.update(
        new EditProductRequest(id, "Must not persist", new BigDecimal("8.00"), 9, 0),
        List.of("")));
    assertEquals(before, repository.findById(id).orElseThrow());
    assertEquals(List.of(label), assigned(id));
  }

  @Test public void deletingTaggedProductPreservesSharedTag() throws Exception {
    int first = add(List.of(label));
    int second = add(List.of(label));
    assertTrue(repository.delete(new DeleteProductRequest(first)));
    assertTrue(repository.findById(first).isEmpty());
    assertTrue(assigned(first).isEmpty());
    assertEquals(List.of(label), assigned(second));
    assertTrue(tags.catalog().labels().contains(label));
  }

  @Test public void rolledBackTagReplacementPreservesOriginalAssignments() throws Exception {
    int id = add(List.of(label));
    connection.setAutoCommit(false);
    try {
      TagRepository.replace(connection, id, List.of(label + "-rollback"));
      connection.rollback();
    } finally {
      connection.setAutoCommit(true);
    }
    assertEquals(List.of(label), assigned(id));
    assertFalse(tags.catalog().labels().contains(label + "-rollback"));
  }

  @Test public void newColorPersistsAndReusePreservesIt() throws Exception {
    int first = repository.add(new AddProductRequest(
        "Color QA " + UUID.randomUUID(), new BigDecimal("5.00"), 1, 0),
        List.of(label), Map.of(label, "yellow"));
    ids.add(first);
    assertEquals("yellow", tags.catalog().colors().get(label));
    int second = repository.add(new AddProductRequest(
        "Reuse QA " + UUID.randomUUID(), new BigDecimal("5.00"), 1, 0),
        List.of(label.toLowerCase(Locale.ROOT)), Map.of(label.toLowerCase(Locale.ROOT), "blue"));
    ids.add(second);
    assertEquals(List.of(label), assigned(second));
    assertEquals("yellow", tags.catalog().colors().get(label));
    assertTrue(repository.update(new EditProductRequest(
        first, "Legacy color QA", new BigDecimal("6.00"), 2, 0), List.of(label)));
    assertEquals("yellow", tags.catalog().colors().get(label));
    assertTrue(repository.delete(new DeleteProductRequest(first)));
    assertEquals("yellow", tags.catalog().colors().get(label));
  }

  @Test public void invalidColorDoesNotChangeProduct() throws Exception {
    int id = add(List.of(label));
    var before = repository.findById(id).orElseThrow();
    assertThrows(IllegalArgumentException.class, () -> repository.update(
        new EditProductRequest(id, "Must not persist", new BigDecimal("8.00"), 9, 0),
        List.of(label), Map.of(label, "not-a-color")));
    assertEquals(before, repository.findById(id).orElseThrow());
    assertEquals("green", tags.catalog().colors().get(label));
  }

  @Test public void rolledBackColorCreationLeavesNoTag() throws Exception {
    int id = add(List.of(label));
    connection.setAutoCommit(false);
    try {
      TagRepository.replace(connection, id, List.of(label + "-rollback"),
          Map.of(label + "-rollback", "yellow"));
      connection.rollback();
    } finally {
      connection.setAutoCommit(true);
    }
    assertEquals(List.of(label), assigned(id));
    assertFalse(tags.catalog().colors().containsKey(label + "-rollback"));
  }

  @After public void tearDown() throws Exception {
    if (connection == null) return;
    try {
      connection.setAutoCommit(false);
      for (int id : ids) {
        for (String table : new String[] {"TagFor", "ProductHistory", "Product"}) {
          try (PreparedStatement s = connection.prepareStatement(
              "DELETE FROM ONLY public.\"" + table + "\" WHERE product_id = ?")) {
            s.setInt(1, id);
            s.executeUpdate();
          }
        }
      }
      try (PreparedStatement s = connection.prepareStatement(
          "DELETE FROM ONLY public.\"Tag\" WHERE LOWER(label) IN (LOWER(?), LOWER(?))")) {
        s.setString(1, label);
        s.setString(2, label + "-rollback");
        s.executeUpdate();
      }
      connection.commit();
    } catch (Exception error) {
      connection.rollback();
      throw error;
    } finally {
      connection.close();
    }
  }
}
