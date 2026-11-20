# Database backups (beta)

The Database page creates and lists persistent business-data snapshots and loads a
selected snapshot after confirmation. It uses the existing MM_DB_URL, MM_DB_USER,
and MM_DB_PASSWORD environment variables; pg_dump/pg_restore are not required.

## Scope

Snapshots contain the nine public business tables: Product, Tag, Material, Sale,
TagFor, ProductHistory, TagHistory, MaterialHistory, and SaleHistory. Each is read
with ONLY to avoid duplicating inherited history rows. All current columns are
included, including production_cost_per_unit when installed. This is a data-only
application backup, not a SQL dump or a PostgreSQL server/role/schema backup.

Backups use a versioned .mmbak format with a SHA-256 checksum. They are written to
a temporary file and atomically renamed after completion. By default they live
under ~/.mildmarketeers/backups in a subdirectory unique to the connection URL and
database name. Set MM_BACKUP_DIR to override the root. Keep this directory private
and outside the web root and Git. These files may contain customer email addresses.
Backups survive application restart. Use a stable connection URL to find the same
backup collection. Copying the backup folder to separate storage protects against
loss of the machine; backups on the same disk alone do not.

The beta supports at most 64 MiB and 100,000 total rows per snapshot. Additional
public tables are rejected explicitly so a snapshot cannot silently omit new
business tables. Schema differences require an explicit migration strategy and
are rejected. SQL dumps and external uploads are not accepted by these endpoints.

## Restore behavior

The file checksum, format, database identity, and current column names/types/order
are checked. All table replacement and insertion happen in ONE JDBC transaction.
ACCESS EXCLUSIVE locks block concurrent database writes during restore. Lock waits
are limited to five seconds, individual statements to thirty seconds. Unexpected
foreign-key dependencies, schema mismatches, insertion failures, and corrupt files
cause the operation to fail; database changes are rolled back. Parent products are
inserted before children (the application supports one level of subproducts).
No schema or table is dropped and TRUNCATE does not use CASCADE.

Users should create a current backup before loading an older one if they might
want to return to the current state. A successful load replaces all nine tables,
including histories. Open product/sales/summary pages should be refreshed afterward.

The UI uses Julia's confirmAction utility. Placeholder database paths, sample
backups, and sample change logs are removed. Import/export, backup deletion, and
change-log reversion are outside this beta implementation.

This app currently has no user authentication. Run it as a local single-user app
(bound to localhost, e.g. SERVER_ADDRESS=127.0.0.1); do not expose database-management
endpoints to a public/shared network without authentication and authorization.
The required X-MM-Backup header blocks ordinary cross-origin HTML form submissions;
it is not a substitute for authentication. Do not enable permissive CORS for it.

## API

- GET /api/database/backups: database name and backup list.
- POST /api/database/backups with X-MM-Backup: 1: create snapshot.
- POST /api/database/backups/{uuid}/load with X-MM-Backup: 1: restore snapshot.

## Verification

Compile with `bash gradlew :app:classes :app:testClasses`.

The integration test intentionally rebuilds the public schema ONLY in a database
named mildmarketeers_backup_test. NEVER put business records in that database.
Create it once, owned by the existing application role:

```bash
sudo -u postgres createdb -O mildmarketeers_app mildmarketeers_backup_test
```

Export MM_DB_USER and MM_DB_PASSWORD as usual, then run:

```bash
MM_BACKUP_TEST_URL=jdbc:postgresql://localhost:5432/mildmarketeers_backup_test \
  bash gradlew :app:test --tests org.example.backup.BackupServiceTest --rerun-tasks
```

Five tests verify all-table round-trip (including saved costs and parent/child
relations), file corruption, schema mismatch, rollback after a failed insertion,
and missing-file/path-traversal rejection. The test skips unless MM_BACKUP_TEST_URL
is set, and refuses every database name other than mildmarketeers_backup_test.
Its temporary backup files are separate from the user's backup directory.

For browser testing, launch the app against that same disposable database:

```bash
MM_DB_URL=jdbc:postgresql://localhost:5432/mildmarketeers_backup_test \
  SERVER_PORT=8081 SERVER_ADDRESS=127.0.0.1 \
  bash gradlew :app:bootRun --no-daemon --no-configuration-cache
```

At http://localhost:8081/database.html: create a backup, change a product's stock,
click Load and Cancel (stock must remain changed), then confirm Load (stock must
return to the snapshot value). Restart the app and verify the backup is listed
and restored data remains. Check Products and Sales after reloading those pages.
Do not run the integration tests while doing browser checks: they reset fixtures.

Local preparation checks: Java service compiled with Java 21; JavaScript syntax
checked; binary format round-trip/null/Unicode and corrupt/empty rejection checks
passed. Full Gradle compilation and PostgreSQL integration tests must run on the
developer machine: the preparation environment could not download Gradle and has
no PostgreSQL server.
