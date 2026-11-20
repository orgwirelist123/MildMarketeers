package org.mildmarketeers.controller;

import java.sql.SQLException;
import java.util.Map;
import org.example.tags.TagRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Supplies existing tags and product assignments to the product UI. */
@RestController
@RequestMapping("/api/tags")
public class TagController {
	private final TagRepository repository = new TagRepository();

	@GetMapping
	public TagRepository.Catalog catalog() throws SQLException {
		return repository.catalog();
	}

	@ExceptionHandler(SQLException.class)
	public ResponseEntity<Map<String, String>> failure(SQLException error) {
		org.slf4j.LoggerFactory.getLogger(TagController.class).error("Could not load tags.", error);
		return ResponseEntity.status(500).body(Map.of("message", "Could not load tags. Please try again."));
	}
}
