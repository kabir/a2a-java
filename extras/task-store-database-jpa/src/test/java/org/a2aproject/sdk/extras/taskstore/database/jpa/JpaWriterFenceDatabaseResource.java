package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Disposable PostgreSQL 16 with a faithful pre-upgrade state: the old identity {@code a2a_old} owns the
 * legacy six-column task schema. The container superuser is the operator that later runs the upgrade DDL,
 * ownership transfer and REVOKE; the SDK application connects as {@code a2a_new}. Startup fails when
 * Docker/Podman is unavailable.
 */
public final class JpaWriterFenceDatabaseResource implements QuarkusTestResourceLifecycleManager {

    static final String OLD_USER = "a2a_old";
    static final String NEW_USER = "a2a_new";
    static final String PASSWORD = "a2a-secret";

    private PostgreSQLContainer database;

    @Override
    public Map<String, String> start() {
        database = new PostgreSQLContainer("postgres:16");
        database.start();
        try (Connection admin = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword()); Statement statement = admin.createStatement()) {
            statement.execute("CREATE ROLE " + OLD_USER + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("CREATE ROLE " + NEW_USER + " LOGIN PASSWORD '" + PASSWORD + "'");
            statement.execute("GRANT ALL ON SCHEMA public TO " + OLD_USER);
            statement.execute("GRANT ALL ON SCHEMA public TO " + NEW_USER);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the old/new database identities", e);
        }
        // Pre-upgrade schema created by the OLD identity (it is the owner), exactly the legacy six columns.
        try (Connection old = DriverManager.getConnection(database.getJdbcUrl(), OLD_USER, PASSWORD);
                Statement statement = old.createStatement()) {
            statement.execute("CREATE TABLE a2a_tasks (task_id VARCHAR(255) PRIMARY KEY, context_id VARCHAR(255), "
                    + "state VARCHAR(255), status_timestamp TIMESTAMP, task_data TEXT NOT NULL, finalized_at TIMESTAMP)");
            statement.execute("CREATE TABLE a2a_task_owners (task_id VARCHAR(255) PRIMARY KEY, "
                    + "subject_key BYTEA NOT NULL)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the pre-upgrade schema", e);
        }
        Map<String, String> properties = new HashMap<>();
        properties.put("quarkus.datasource.\"a2a-java\".jdbc.url", database.getJdbcUrl());
        properties.put("quarkus.datasource.\"a2a-java\".username", NEW_USER);
        properties.put("quarkus.datasource.\"a2a-java\".password", PASSWORD);
        properties.put("t09.admin.user", database.getUsername());
        properties.put("t09.admin.password", database.getPassword());
        properties.put("t09.jdbc.url", database.getJdbcUrl());
        return properties;
    }

    @Override
    public void stop() {
        if (database != null) {
            database.stop();
        }
    }
}
