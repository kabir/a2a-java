package org.a2aproject.sdk.extras.taskstore.database.jpa;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Disposable PostgreSQL database for shared webhook dispatch gate tests. */
public final class JpaWebhookDispatchDatabaseResource implements QuarkusTestResourceLifecycleManager {
    private PostgreSQLContainer database;

    @Override
    public Map<String, String> start() {
        database = new PostgreSQLContainer("postgres:16");
        database.start();
        return Map.of(
                "quarkus.datasource.\"a2a-java\".jdbc.url", database.getJdbcUrl(),
                "quarkus.datasource.\"a2a-java\".username", database.getUsername(),
                "quarkus.datasource.\"a2a-java\".password", database.getPassword());
    }

    @Override
    public void stop() {
        if (database != null) {
            database.stop();
        }
    }
}
