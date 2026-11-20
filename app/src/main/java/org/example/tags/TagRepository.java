package org.example.tags;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.example.database.DatabaseConnection;

/** Reads tag labels and saves associations within the product transaction. */
public final class TagRepository {
	public record Catalog(List<String> labels, Map<Integer, List<String>> products,
			Map<String, String> colors) {}

	public Catalog catalog() throws SQLException {
		List<String> labels = new ArrayList<>();
		Map<String, String> colors = new HashMap<>();
		Map<Integer, List<String>> products = new HashMap<>();
		try (Connection c = DatabaseConnection.open()) {
			try (PreparedStatement s = c.prepareStatement(
					"SELECT label, color FROM ONLY public.\"Tag\" ORDER BY LOWER(label), tag_id")) {
				s.setQueryTimeout(5);
				try (ResultSet r = s.executeQuery()) {
					while (r.next()) {
						labels.add(r.getString(1));
						colors.put(r.getString(1), r.getString(2));
					}
				}
			}
			try (PreparedStatement s = c.prepareStatement("""
					SELECT f.product_id, t.label
					FROM ONLY public."TagFor" f
					JOIN ONLY public."Tag" t ON t.tag_id = f.tag_id
					ORDER BY LOWER(t.label), t.tag_id
					""")) {
				s.setQueryTimeout(5);
				try (ResultSet r = s.executeQuery()) {
					while (r.next()) products.computeIfAbsent(r.getInt(1), k -> new ArrayList<>())
							.add(r.getString(2));
				}
			}
		}
		return new Catalog(labels, products, colors);
	}

	/** Null means preserve assignments; an empty list explicitly removes them. */
	public static List<String> validate(List<String> labels) {
		if (labels == null) return null;
		if (labels.size() > 50) throw new IllegalArgumentException("Choose at most 50 tags.");
		Map<String, String> unique = new LinkedHashMap<>();
		for (String label : labels) {
			if (label == null || label.isBlank() || label.strip().length() > 100
					|| label.chars().anyMatch(Character::isISOControl)) {
				throw new IllegalArgumentException("Tag names must contain 1 to 100 characters without control characters.");
			}
			String clean = label.strip();
			unique.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
		}
		return new ArrayList<>(unique.values());
	}

	/** Validates colors before any product mutation; keys are normalized tag names. */
	public static Map<String, String> validateColors(List<String> labels, Map<String, String> colors) {
		if (colors == null || colors.isEmpty()) return Map.of();
		if (labels == null) throw new IllegalArgumentException("Colors require tag assignments.");
		Set<String> names = new HashSet<>();
		for (String label : labels) names.add(label.toLowerCase(Locale.ROOT));
		Map<String, String> clean = new HashMap<>();
		Set<String> allowed = Set.of("green", "blue", "purple", "pink", "red", "orange", "yellow", "gray");
		for (var entry : colors.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null
					|| !allowed.contains(entry.getValue())) {
				throw new IllegalArgumentException("Choose a supported tag color.");
			}
			String name = entry.getKey().strip().toLowerCase(Locale.ROOT);
			if (!names.contains(name)) throw new IllegalArgumentException("Colors must belong to selected tags.");
			if (clean.containsKey(name) && !clean.get(name).equals(entry.getValue())) {
				throw new IllegalArgumentException("Conflicting colors for the same tag.");
			}
			clean.put(name, entry.getValue());
		}
		return clean;
	}

	/** Caller owns the transaction and already holds the product lock. */
	public static void replace(Connection c, int productId, List<String> labels)
			throws SQLException {
		replace(c, productId, labels, Map.of());
	}

	/** Existing shared tags keep their color; colors apply only when creating new tags. */
	public static void replace(Connection c, int productId, List<String> labels,
			Map<String, String> colors) throws SQLException {
		labels = validate(labels);
		colors = validateColors(labels, colors);
		if (labels == null) return;
		try (PreparedStatement s = c.prepareStatement(
				"LOCK TABLE public.\"Tag\" IN SHARE ROW EXCLUSIVE MODE")) {
			s.setQueryTimeout(5);
			s.execute();
		}
		try (PreparedStatement s = c.prepareStatement(
				"DELETE FROM ONLY public.\"TagFor\" WHERE product_id = ?")) {
			s.setQueryTimeout(5);
			s.setInt(1, productId);
			s.executeUpdate();
		}
		for (String label : labels) {
			int tagId = 0;
			try (PreparedStatement s = c.prepareStatement(
					"SELECT tag_id FROM ONLY public.\"Tag\" WHERE LOWER(label) = LOWER(?) ORDER BY tag_id LIMIT 1")) {
				s.setQueryTimeout(5);
				s.setString(1, label);
				try (ResultSet r = s.executeQuery()) {
					if (r.next()) tagId = r.getInt(1);
				}
			}
			if (tagId == 0) {
				boolean inserted = false;
				for (int attempt = 0; attempt < 10 && !inserted; attempt++) {
					tagId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
					try (PreparedStatement s = c.prepareStatement(
							"INSERT INTO public.\"Tag\" (tag_id, label, color) VALUES (?, ?, ?) ON CONFLICT (tag_id) DO NOTHING")) {
						s.setQueryTimeout(5);
						s.setInt(1, tagId);
						s.setString(2, label);
						s.setString(3, colors.getOrDefault(label.toLowerCase(Locale.ROOT), "green"));
						inserted = s.executeUpdate() == 1;
					}
				}
				if (!inserted) throw new SQLException("Could not allocate a tag ID.");
			}
			try (PreparedStatement s = c.prepareStatement(
					"INSERT INTO public.\"TagFor\" (tag_id, product_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
				s.setQueryTimeout(5);
				s.setInt(1, tagId);
				s.setInt(2, productId);
				s.executeUpdate();
			}
		}
	}
}
