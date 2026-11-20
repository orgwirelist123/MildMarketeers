package org.example.backup;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.Before;
import org.junit.Test;

/** Destructive fixtures are allowed ONLY in the explicitly named isolated test database. */
public class BackupServiceTest {
  private Path directory;
  private BackupService service;
  private static final List<String> TABLES = List.of("Product", "Tag", "Material", "Sale",
      "TagFor", "ProductHistory", "TagHistory", "MaterialHistory", "SaleHistory");

  private Connection open() throws SQLException {
    return DriverManager.getConnection(System.getenv("MM_BACKUP_TEST_URL"),
        System.getenv("MM_DB_USER"), System.getenv("MM_DB_PASSWORD"));
  }

  @Before
  public void setup() throws Exception {
    assumeTrue("Set MM_BACKUP_TEST_URL to the isolated mildmarketeers_backup_test database.",
        System.getenv("MM_BACKUP_TEST_URL") != null);
    try (Connection c = open(); Statement s = c.createStatement()) {
      try (ResultSet r = s.executeQuery("SELECT current_database()")) {
        r.next();
        if (!"mildmarketeers_backup_test".equals(r.getString(1)))
          throw new IllegalStateException("Refusing to modify any database except mildmarketeers_backup_test.");
      }
      s.execute("DROP SCHEMA public CASCADE");
      s.execute("CREATE SCHEMA public");
      Path schema = Path.of("../schema.sql");
      if (!Files.exists(schema)) schema = Path.of("schema.sql");
      s.execute(Files.readString(schema));
      s.execute("ALTER TABLE public.\"Sale\" ADD COLUMN IF NOT EXISTS production_cost_per_unit numeric(12,2)");
      s.execute("""
          INSERT INTO "Product" VALUES (1,'Parent',5,0,10,NULL,FALSE),(2,'Child',8,1,4,1,FALSE);
          INSERT INTO "Tag" VALUES (7,'Yellow','yellow');
          INSERT INTO "Material" VALUES ('Ingredient',1,2,'unit',1,1);
          INSERT INTO "Sale" (sale_id,product_id,date,time,quantity,unit_price,discount,email,production_cost_per_unit)
            VALUES (100,2,CURRENT_DATE,'12:00',1,8,1,NULL,3);
          INSERT INTO "TagFor" VALUES (7,2);
          INSERT INTO "ProductHistory" SELECT p.*,11,CURRENT_DATE,CURRENT_TIMESTAMP FROM ONLY "Product" p WHERE product_id=2;
          INSERT INTO "TagHistory" SELECT t.*,12,CURRENT_DATE,LOCALTIME FROM ONLY "Tag" t;
          INSERT INTO "MaterialHistory" SELECT m.*,13,CURRENT_DATE,LOCALTIME FROM ONLY "Material" m;
          INSERT INTO "SaleHistory" (sale_id,product_id,date,time,quantity,unit_price,discount,email,
            production_cost_per_unit,change_id,change_date,change_time)
            SELECT sale_id,product_id,date,time,quantity,unit_price,discount,email,
            production_cost_per_unit,14,CURRENT_DATE,LOCALTIME FROM ONLY "Sale";
          """);
    }
    directory = Files.createTempDirectory("mm-backup-test-");
    service = new BackupService(directory, this::open);
  }

  private List<String> records() throws Exception {
    List<String> rows = new ArrayList<>();
    try (Connection c = open(); Statement s = c.createStatement()) {
      for (String table : TABLES) {
        try (ResultSet r = s.executeQuery("SELECT row_to_json(t)::text FROM ONLY public.\"" + table + "\" t")) {
          while (r.next()) rows.add(table + ":" + r.getString(1));
        }
      }
    }
    Collections.sort(rows);
    return rows;
  }

  private void execute(String sql) throws Exception {
    try (Connection c = open(); Statement s = c.createStatement()) { s.execute(sql); }
  }

  @Test
  public void roundTripRestoresAllTablesAndSavedCostsAfterServiceRestart() throws Exception {
    List<String> before = records();
    var backup = service.create();
    assertEquals(before, records());
    execute("UPDATE ONLY \"Product\" SET inventory=3; DELETE FROM ONLY \"TagFor\"; UPDATE ONLY \"Sale\" SET production_cost_per_unit=9");
    assertNotEquals(before, records());
    service = new BackupService(directory, this::open);
    assertEquals(1, ((List<?>) service.list().get("backups")).size());
    service.restore(backup.id());
    assertEquals(before, records());
  }

  @Test
  public void corruptFileLeavesEveryTableUnchanged() throws Exception {
    var backup = service.create();
    Path file;
    try (var files = Files.walk(directory)) {
      file = files.filter(p -> p.toString().endsWith(".mmbak")).findFirst().orElseThrow();
    }
    Files.writeString(file, "not a valid backup");
    List<String> before = records();
    assertThrows(IllegalArgumentException.class, () -> service.restore(backup.id()));
    assertEquals(before, records());
  }

  @Test
  public void schemaMismatchLeavesEveryTableUnchanged() throws Exception {
    var backup = service.create();
    execute("ALTER TABLE \"Product\" ADD COLUMN test_extra text");
    List<String> before = records();
    assertThrows(IllegalArgumentException.class, () -> service.restore(backup.id()));
    assertEquals(before, records());
  }

  @Test
  public void insertFailureRollsBackTruncateAndRestoresCurrentRecords() throws Exception {
    var backup = service.create();
    execute("UPDATE ONLY \"Product\" SET inventory=3");
    execute("ALTER TABLE ONLY \"Product\" ADD CONSTRAINT test_limit CHECK(inventory<5) NO INHERIT NOT VALID");
    List<String> before = records();
    assertThrows(SQLException.class, () -> service.restore(backup.id()));
    assertEquals(before, records());
  }

  @Test
  public void missingFileAndTraversalAreRejected() throws Exception {
    List<String> before = records();
    assertThrows(IllegalArgumentException.class, () -> service.restore(UUID.randomUUID().toString()));
    assertThrows(IllegalArgumentException.class, () -> service.restore("../../schema.sql"));
    assertEquals(before, records());
  }
}
