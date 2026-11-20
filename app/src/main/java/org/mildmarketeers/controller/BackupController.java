package org.mildmarketeers.controller;

import java.util.Map;
import org.example.backup.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Local application's database backup API. */
@RestController
@RequestMapping("/api/database/backups")
public class BackupController {
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupController.class);
  private final BackupService service = new BackupService();

  @GetMapping
  public Map<String, Object> list() throws Exception { return service.list(); }

  // A custom header prevents cross-origin HTML forms from invoking destructive actions.
  @PostMapping(headers = "X-MM-Backup=1")
  public BackupService.BackupInfo create() throws Exception { return service.create(); }

  @PostMapping(value = "/{id}/load", headers = "X-MM-Backup=1")
  public Map<String, String> load(@PathVariable("id") String id) throws Exception {
    service.restore(id);
    return Map.of("message", "Backup loaded. Business data has been restored.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> failure(Exception exception) {
    LOGGER.error("Database backup operation failed.", exception);
    return ResponseEntity.status(500).body(Map.of("message",
        "Backup operation failed. If loading failed, the database transaction was rolled back. Please try again."));
  }
}
