# PostgreSQL Database Setup

This guide explains how to set up a local PostgreSQL database and test
the Java backend connection.

## Requirements

- Java 21
- PostgreSQL
- The MildMarketeers repository with its Gradle wrapper

This setup was tested using PostgreSQL 14.24 on Ubuntu 22.04 in WSL.
The commands below are for Ubuntu/WSL.

## 1. Install and Start PostgreSQL

```bash
sudo apt update
sudo apt install postgresql postgresql-client
sudo service postgresql start
```

Check the installed server version:

```bash
(cd /tmp && sudo -u postgres psql -c "SELECT version();")
```

If the output opens in a pager showing `(END)`, press `q` to exit.

## 2. Create the Application Login and Database

Create a dedicated login:

```bash
(cd /tmp && sudo -u postgres createuser --pwprompt mildmarketeers_app)
```

Choose and confirm your own local password. Do not commit passwords
to Git or share them in documentation.

Create the database:

```bash
(cd /tmp && sudo -u postgres createdb --owner=mildmarketeers_app mildmarketeers)
```

These creation commands only need to run once. If the login or database
already exists, verify its configuration rather than recreating it.

## 3. Verify the Database Login

```bash
psql -h localhost -p 5432 -U mildmarketeers_app -d mildmarketeers -W -c "SELECT current_database(), current_user;"
```

Enter the password you chose. The result should show:

- Database: mildmarketeers
- User: mildmarketeers_app

## 4. Configure the Java Connection

Run these commands in the terminal where you will run Gradle:

```bash
export MM_DB_URL='jdbc:postgresql://localhost:5432/mildmarketeers'
export MM_DB_USER='mildmarketeers_app'
read -rsp "Database password: " MM_DB_PASSWORD
echo
export MM_DB_PASSWORD
```

Type your database password at the prompt. It will not appear on screen.

These variables apply to the current terminal session and its child
processes. Set them again when opening a new terminal.

The PostgreSQL JDBC driver is declared in `app/build.gradle.kts`.
Gradle downloads it automatically.

## 5. Use the Java Connection Class

Connection class:

`app/src/main/java/org/example/database/DatabaseConnection.java`

Example:

```java
import java.sql.Connection;
import java.sql.SQLException;
import org.example.database.DatabaseConnection;

// Inside a method that handles or declares SQLException:
try (Connection connection = DatabaseConnection.open()) {
  // Execute database operations here.
}
```

`open()` returns a new connection. Use try-with-resources to close it
after use.

Missing configuration causes an IllegalStateException.
Database connection failures cause an SQLException.

## 6. Run the Connection Integration Test

From the repository root, with the environment variables set:

```bash
MM_DB_TEST=true bash gradlew test \
  --tests org.example.database.DatabaseConnectionTest \
  --rerun-tasks
```

This test opens a database connection, executes `SELECT 1`, checks
the result, and closes the connection. It does not modify records.

The test is skipped unless `MM_DB_TEST=true` is set.

Verify that it ran rather than being skipped:

```bash
head -n 2 app/build/test-results/test/TEST-org.example.database.DatabaseConnectionTest.xml
```

Expected test counts:

- tests="1"
- skipped="0"
- failures="0"
- errors="0"

To run the normal test suite without opting into the database test:

```bash
unset MM_DB_TEST
bash gradlew test
```

## 7. Schema Status

The database connection has been tested successfully.

The project schema is being prepared separately. Import instructions
will be added after the schema export has been received and tested.
A successful connection test does not verify application tables.

## Troubleshooting

### Missing required environment variable

Set MM_DB_URL, MM_DB_USER, and MM_DB_PASSWORD in the same terminal
used to run Gradle.

### Connection refused

Check that PostgreSQL is running:

```bash
sudo service postgresql status
sudo service postgresql start
```

Verify the host and port in MM_DB_URL.

### Password authentication failed

Check MM_DB_USER and re-enter the password:

```bash
read -rsp "Database password: " MM_DB_PASSWORD
echo
export MM_DB_PASSWORD
```

### Permission denied when running ./gradlew

Run the wrapper through Bash:

```bash
bash gradlew test
```

### Could not change directory when using sudo -u postgres

Use the `(cd /tmp && ...)` form shown above. It avoids the postgres
Linux user needing access to your home directory.
