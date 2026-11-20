package org.example.backup;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.example.database.DatabaseConnection;

/** Creates data-only snapshots; restores compatible snapshots atomically. */
public final class BackupService {
	private static final List<String> TABLES = List.of("Product", "Tag", "Material", "Sale",
			"TagFor", "ProductHistory", "TagHistory", "MaterialHistory", "SaleHistory");
	private static final int MAX_BYTES = 64 * 1024 * 1024;
	private static final String MAGIC = "MildMarketeers backup v1";
	private final Path directory;
	private final ConnectionFactory connections;

	@FunctionalInterface
	public interface ConnectionFactory { Connection open() throws SQLException; }
	public record BackupInfo(String id, String createdAt, long bytes) {}
	private record Column(String name, String type) {}
	private record Table(String name, List<Column> columns, List<List<String>> rows) {}
	private record Snapshot(String database, String createdAt, List<Table> tables) {}

	public BackupService() {
		this(Path.of(System.getenv().getOrDefault("MM_BACKUP_DIR",
				Path.of(System.getProperty("user.home"), ".mildmarketeers", "backups").toString())),
				DatabaseConnection::open);
	}

	public BackupService(Path directory, ConnectionFactory connections) {
		this.directory = directory.toAbsolutePath().normalize();
		this.connections = connections;
	}

	/** Lists backups for the configured database, newest first. */
	public synchronized Map<String, Object> list() throws Exception {
		try (Connection c = connections.open()) {
			String database = database(c);
			Path folder = folder(c);
			List<BackupInfo> backups = new ArrayList<>();
			try (var paths = Files.list(folder)) {
				for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".mmbak")).toList()) {
					String id = path.getFileName().toString().replace(".mmbak", "");
					if (!id.matches("[0-9a-f-]{36}") || Files.isSymbolicLink(path)) continue;
					// Keep unreadable/corrupt backups visible; loading validates their full contents.
					backups.add(new BackupInfo(id, Files.getLastModifiedTime(path).toInstant().toString(),
							Files.size(path)));
				}
			}
			backups.sort(Comparator.comparing(BackupInfo::createdAt).reversed());
			return Map.of("database", database, "backups", backups);
		}
	}

	/** Saves a consistent snapshot without changing business records. */
	public synchronized BackupInfo create() throws Exception {
		try (Connection c = connections.open()) {
			c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			c.setReadOnly(true);
			c.setAutoCommit(false);
			configure(c);
			List<Table> tables = schema(c);
			List<Table> saved = new ArrayList<>();
			long estimatedBytes = 0;
			long totalRows = 0;
			for (Table table : tables) {
				List<List<String>> rows = new ArrayList<>();
				String order = table.name().equals("Product") ? " ORDER BY parent_id NULLS FIRST, product_id" : "";
				try (Statement s = c.createStatement();
						ResultSet r = s.executeQuery("SELECT * FROM ONLY public." + quote(table.name()) + order)) {
					while (r.next()) {
						List<String> row = new ArrayList<>();
						for (int i = 1; i <= table.columns().size(); i++) {
							String value = r.getString(i);
							estimatedBytes += 4L + (value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
							if (estimatedBytes > MAX_BYTES - 65536) throw new IOException("Backup exceeds the 64 MiB limit.");
							row.add(value);
						}
						if (++totalRows > 100000) throw new IOException("Backup exceeds the 100,000 record limit.");
						rows.add(row);
					}
				}
				saved.add(new Table(table.name(), table.columns(), rows));
			}
			Snapshot snapshot = new Snapshot(database(c), Instant.now().toString(), saved);
			byte[] bytes = encode(snapshot);
			Path folder = folder(c);
			c.commit();
			String id = UUID.randomUUID().toString();
			Path temporary = Files.createTempFile(folder, ".pending-", ".tmp");
			try {
				Files.write(temporary, bytes);
				Files.move(temporary, folder.resolve(id + ".mmbak"), StandardCopyOption.ATOMIC_MOVE);
			} finally { Files.deleteIfExists(temporary); }
			return new BackupInfo(id, snapshot.createdAt(), bytes.length);
		}
	}

	/** Validates the file and restores all records, or rolls back every database change. */
	public synchronized void restore(String id) throws Exception {
		if (id == null || !id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
			throw new IllegalArgumentException("Invalid backup identifier.");
		try (Connection c = connections.open()) {
			Path file = folder(c).resolve(id + ".mmbak");
			if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > MAX_BYTES)
				throw new IllegalArgumentException("Backup is missing, unreadable, or too large.");
			Snapshot snapshot;
			try { snapshot = decode(Files.readAllBytes(file)); }
			catch (IOException | RuntimeException ex) {
				throw new IllegalArgumentException("Backup is invalid or damaged. Existing data was not changed.", ex);
			}
			if (!snapshot.database().equals(database(c)))
				throw new IllegalArgumentException("This backup belongs to a different database.");
			c.setAutoCommit(false);
			try {
				configure(c);
				// Locks also block other application instances from writing during restoration.
				try (Statement s = c.createStatement()) {
					s.execute("LOCK TABLE " + tableNames() + " IN ACCESS EXCLUSIVE MODE");
				}
				List<Table> current = schema(c);
				for (int i = 0; i < current.size(); i++) {
					Table expected = current.get(i), actual = snapshot.tables().get(i);
					if (!expected.name().equals(actual.name()) || !expected.columns().equals(actual.columns()))
						throw new IllegalArgumentException("Backup schema differs from this database. Existing data was not changed.");
				}
				try (Statement s = c.createStatement()) {
					// No CASCADE: unexpected dependencies cause rollback instead of deleting other data.
					s.execute("TRUNCATE TABLE " + tableNames());
				}
				for (Table table : snapshot.tables()) {
					String columns = String.join(",", table.columns().stream().map(v -> quote(v.name())).toList());
					String placeholders = String.join(",", Collections.nCopies(table.columns().size(), "?"));
					try (PreparedStatement s = c.prepareStatement("INSERT INTO public." + quote(table.name())
							+ " (" + columns + ") VALUES (" + placeholders + ")")) {
						for (List<String> row : table.rows()) {
							for (int i = 0; i < row.size(); i++) {
								if (row.get(i) == null) s.setNull(i + 1, Types.NULL);
								else s.setObject(i + 1, row.get(i), Types.OTHER);
							}
							s.executeUpdate();
						}
					}
				}
				c.commit();
			} catch (Exception ex) {
				try { c.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
				throw ex;
			}
		}
	}

	private static void configure(Connection c) throws SQLException {
		try (Statement s = c.createStatement()) {
			s.execute("SET LOCAL statement_timeout = '30s'");
			s.execute("SET LOCAL lock_timeout = '5s'");
			s.execute("SET LOCAL DateStyle = 'ISO, YMD'");
			s.execute("SET LOCAL TIME ZONE 'UTC'");
		}
	}

	private static List<Table> schema(Connection c) throws SQLException {
		Set<String> found = new HashSet<>();
		try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
				"SELECT tablename FROM pg_tables WHERE schemaname = 'public'")) {
			while (r.next()) found.add(r.getString(1));
		}
		if (!found.equals(new HashSet<>(TABLES)))
			throw new IllegalArgumentException("Database tables differ from the supported business schema. Backup operation stopped.");
		List<Table> tables = new ArrayList<>();
		for (String name : TABLES) {
			List<Column> columns = new ArrayList<>();
			try (PreparedStatement s = c.prepareStatement("""
					SELECT a.attname, format_type(a.atttypid, a.atttypmod)
					FROM pg_attribute a JOIN pg_class t ON t.oid=a.attrelid
					JOIN pg_namespace n ON n.oid=t.relnamespace
					WHERE n.nspname='public' AND t.relname=? AND a.attnum>0 AND NOT a.attisdropped
					ORDER BY a.attnum
					""")) {
				s.setString(1, name);
				try (ResultSet r = s.executeQuery()) {
					while (r.next()) columns.add(new Column(r.getString(1), r.getString(2)));
				}
			}
			tables.add(new Table(name, columns, List.of()));
		}
		return tables;
	}

	private Path folder(Connection c) throws Exception {
		// Separate databases/servers using the same backup directory.
		String identity = c.getMetaData().getURL() + "|" + database(c);
		String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		Path folder = directory.resolve(key);
		Files.createDirectories(folder);
		return folder;
	}
	private static String database(Connection c) throws SQLException {
		try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT current_database()")) {
			r.next(); return r.getString(1);
		}
	}
	private static String quote(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
	private static String tableNames() {
		return String.join(",", TABLES.stream().map(v -> "public." + quote(v)).toList());
	}

	private static byte[] encode(Snapshot snapshot) throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(buffer)) {
			out.writeUTF(MAGIC); out.writeUTF(snapshot.database()); out.writeUTF(snapshot.createdAt());
			out.writeInt(snapshot.tables().size());
			for (Table table : snapshot.tables()) {
				out.writeUTF(table.name()); out.writeInt(table.columns().size());
				for (Column column : table.columns()) { out.writeUTF(column.name()); out.writeUTF(column.type()); }
				out.writeInt(table.rows().size());
				for (List<String> row : table.rows()) {
					for (String value : row) {
						if (value == null) out.writeInt(-1);
						else {
							byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
							out.writeInt(bytes.length); out.write(bytes);
						}
					}
					if (buffer.size() > MAX_BYTES - 32) throw new IOException("Backup exceeds the 64 MiB limit.");
				}
			}
			byte[] body = buffer.toByteArray();
			out.write(MessageDigest.getInstance("SHA-256").digest(body));
		}
		return buffer.toByteArray();
	}

	private static Snapshot decode(byte[] bytes) throws Exception {
		if (bytes.length < 32 || bytes.length > MAX_BYTES) throw new IOException("Invalid size");
		int length = bytes.length - 32;
		if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256")
				.digest(Arrays.copyOf(bytes, length)), Arrays.copyOfRange(bytes, length, bytes.length)))
			throw new IOException("Checksum mismatch");
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes, 0, length))) {
			if (!MAGIC.equals(in.readUTF())) throw new IOException("Unknown format");
			String database = in.readUTF(), created = in.readUTF();
			Instant.parse(created);
			if (in.readInt() != TABLES.size()) throw new IOException("Invalid table count");
			List<Table> tables = new ArrayList<>();
			long totalRows = 0;
			for (String expected : TABLES) {
				String name = in.readUTF();
				if (!name.equals(expected)) throw new IOException("Invalid table");
				int count = in.readInt();
				if (count < 1 || count > 100) throw new IOException("Invalid columns");
				List<Column> columns = new ArrayList<>();
				for (int i = 0; i < count; i++) columns.add(new Column(in.readUTF(), in.readUTF()));
				int size = in.readInt();
				totalRows += size;
				if (size < 0 || totalRows > 100000 || size > in.available() / (4L * count)) throw new IOException("Invalid rows");
				List<List<String>> rows = new ArrayList<>();
				for (int i = 0; i < size; i++) {
					List<String> row = new ArrayList<>();
					for (int j = 0; j < count; j++) {
						int n = in.readInt();
						if (n < -1 || n > in.available()) throw new IOException("Invalid value length");
						row.add(n == -1 ? null : new String(in.readNBytes(n), java.nio.charset.StandardCharsets.UTF_8));
					}
					rows.add(row);
				}
				tables.add(new Table(name, columns, rows));
			}
			if (in.available() != 0) throw new IOException("Unexpected trailing data");
			return new Snapshot(database, created, tables);
		}
	}
}
