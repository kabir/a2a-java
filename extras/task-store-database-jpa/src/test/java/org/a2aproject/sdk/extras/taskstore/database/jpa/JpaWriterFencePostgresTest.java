package org.a2aproject.sdk.extras.taskstore.database.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.tasks.TaskMutationNotCommittedException;
import org.a2aproject.sdk.server.tasks.TaskSerializationException;
import org.a2aproject.sdk.server.tasks.TaskSnapshot;
import org.a2aproject.sdk.spec.Task;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Storage-enforced writer fence on a real, disposable PostgreSQL 16. The operator commands are the exact
 * strings in this class (printed to the test output). The old identity is a different database role
 * ({@code a2a_old}); its write privileges are revoked at the database and PostgreSQL itself must reject the
 * writes (SQLSTATE 42501) before any migration runs. This is a disposable fixture, never live data.
 */
@QuarkusTest
@TestProfile(JpaWriterFencePostgresProfile.class)
@DisabledIfSystemProperty(named = "skipDockerTests", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JpaWriterFencePostgresTest {

    static final String OLD = JpaWriterFenceDatabaseResource.OLD_USER;
    static final String NEW = JpaWriterFenceDatabaseResource.NEW_USER;
    static final String PASSWORD = JpaWriterFenceDatabaseResource.PASSWORD;

    /** Operator step 1 (before the fence): the new identity takes ownership; the old keeps explicit write grants. */
    static final List<String> TAKE_OWNERSHIP = List.of(
            "ALTER TABLE a2a_tasks OWNER TO " + NEW,
            "ALTER TABLE a2a_task_owners OWNER TO " + NEW,
            "GRANT SELECT, INSERT, UPDATE, DELETE ON a2a_tasks, a2a_task_owners TO " + OLD);

    /** Operator step 2: the schema upgrade for the new binaries. */
    static final List<String> UPGRADE_DDL = List.of(
            "ALTER TABLE a2a_tasks ADD COLUMN store_revision BIGINT, ADD COLUMN writer_epoch BIGINT, "
                    + "ADD COLUMN migration_marker VARCHAR(255)",
            "CREATE TABLE a2a_task_revision_counter (counter_id INTEGER PRIMARY KEY, next_block BIGINT NOT NULL)",
            "CREATE TABLE a2a_writer_epoch (epoch_id INTEGER PRIMARY KEY, epoch BIGINT NOT NULL)",
            "CREATE TABLE a2a_webhook_dispatch_lanes (task_id VARCHAR(255) PRIMARY KEY, writer_epoch BIGINT NOT NULL, "
                    + "next_generation BIGINT NOT NULL, holder_token VARCHAR(36), claimed_first_generation BIGINT, "
                    + "claimed_last_generation BIGINT)",
            "CREATE TABLE a2a_webhook_dispatch_losses (loss_id VARCHAR(36) PRIMARY KEY, task_id VARCHAR(255) NOT NULL, "
                    + "first_generation BIGINT NOT NULL, last_generation BIGINT NOT NULL, recorded_at TIMESTAMP NOT NULL, "
                    + "reason VARCHAR(512) NOT NULL)",
            "CREATE INDEX a2a_webhook_dispatch_loss_task ON a2a_webhook_dispatch_losses (task_id, first_generation)",
            "ALTER TABLE a2a_task_revision_counter OWNER TO " + NEW,
            "ALTER TABLE a2a_writer_epoch OWNER TO " + NEW,
            "ALTER TABLE a2a_webhook_dispatch_lanes OWNER TO " + NEW,
            "ALTER TABLE a2a_webhook_dispatch_losses OWNER TO " + NEW);

    /** Operator step 3: the fence. Existing old sessions are terminated so none can keep a stale privilege view. */
    static final List<String> FENCE = List.of(
            "REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON a2a_tasks, a2a_task_owners FROM " + OLD,
            "REVOKE ALL ON a2a_task_revision_counter, a2a_writer_epoch, a2a_webhook_dispatch_lanes, "
                    + "a2a_webhook_dispatch_losses FROM " + OLD,
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = '" + OLD + "'");

    @ConfigProperty(name = "t09.jdbc.url")
    String jdbcUrl;

    @ConfigProperty(name = "t09.admin.user")
    String adminUser;

    @ConfigProperty(name = "t09.admin.password")
    String adminPassword;

    @Inject
    JpaDatabaseTaskStore store;

    @Inject
    JpaTaskMigration migration;

    private Connection admin() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
    }

    private Connection old() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, OLD, PASSWORD);
    }

    private static void run(Connection connection, List<String> statements) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                System.out.println("[T09 operator] " + sql);
                statement.execute(sql);
            }
        }
    }

    private int oldInsert(String id, String json) throws SQLException {
        try (Connection connection = old(); PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO a2a_tasks (task_id, context_id, state, task_data) VALUES (?, ?, 'TASK_STATE_WORKING', ?)")) {
            insert.setString(1, id);
            insert.setString(2, "ctx-" + id);
            insert.setString(3, json);
            return insert.executeUpdate();
        }
    }

    private void assertDenied(String description, SQLExceptionAction action) {
        SQLException denied = assertThrows(SQLException.class, action::run, description);
        assertEquals("42501", denied.getSQLState(), description + ": " + denied.getMessage());
        System.out.println("[T09 denied] " + description + " -> " + denied.getMessage());
    }

    @FunctionalInterface
    interface SQLExceptionAction {
        void run() throws SQLException;
    }

    private String[] rowByAdmin(String id) throws SQLException {
        try (Connection connection = admin(); PreparedStatement select = connection.prepareStatement(
                "SELECT task_data, store_revision, writer_epoch, migration_marker FROM a2a_tasks WHERE task_id = ?")) {
            select.setString(1, id);
            try (ResultSet rs = select.executeQuery()) {
                assertTrue(rs.next(), "row " + id);
                return new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)};
            }
        }
    }

    @Test
    @Order(1)
    void oldIdentityIsDeniedByPostgresBeforeMigrationAndCannotRewriteMigratedData() throws Exception {
        String legacyId = JpaAtomicTaskMutationTest.id();
        Task legacy = JpaTaskMigrationTest.legacyTask(legacyId);
        String legacyDocument = JsonUtil.toJson(legacy);

        try (Connection operator = admin()) {
            run(operator, TAKE_OWNERSHIP);
            run(operator, UPGRADE_DDL);
        }

        // Pre-fence: the old identity can still write, which is exactly what the fence must remove.
        assertEquals(1, oldInsert(legacyId, legacyDocument));
        String legacyJson = rowByAdmin(legacyId)[0];
        Task stored = JsonUtil.fromJson(legacyJson, Task.class); // exactly what the old writer stored

        // The new binary has not activated an epoch: storage stays in legacy mode and nothing is baselined.
        assertEquals(0, store.readSnapshot(legacyId).task().generation());
        assertNull(rowByAdmin(legacyId)[3]);

        try (Connection operator = admin()) {
            run(operator, FENCE);
        }

        // Fenced: PostgreSQL itself denies INSERT, UPDATE and DELETE by the old identity (SQLSTATE 42501).
        assertDenied("old INSERT", () -> oldInsert(JpaAtomicTaskMutationTest.id(), legacyDocument));
        assertDenied("old UPDATE", () -> {
            try (Connection connection = old(); PreparedStatement update = connection.prepareStatement(
                    "UPDATE a2a_tasks SET task_data = ? WHERE task_id = ?")) {
                update.setString(1, legacyJson);
                update.setString(2, legacyId);
                update.executeUpdate();
            }
        });
        assertDenied("old DELETE", () -> {
            try (Connection connection = old(); PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM a2a_tasks WHERE task_id = ?")) {
                delete.setString(1, legacyId);
                delete.executeUpdate();
            }
        });
        assertEquals(legacyJson, rowByAdmin(legacyId)[0], "denied writes left the row untouched");

        // Only now: activate the epoch (as the new identity) and migrate.
        migration.activate(2);
        assertEquals(1, migration.migrateAll());
        String[] migrated = rowByAdmin(legacyId);
        assertEquals(JpaTaskMigration.BASELINE_MARKER, migrated[3]);
        assertEquals("2", migrated[2]);
        assertNotNull(migrated[1]);
        Task baselined = JsonUtil.fromJson(migrated[0], Task.class);
        assertEquals(1, baselined.generation());
        assertEquals(stored.history(), baselined.history());
        assertEquals(stored.artifacts().get(0).parts(), baselined.artifacts().get(0).parts());
        assertNull(baselined.artifacts().get(0).role());

        // A delayed old writer trying to reset the migrated task to generation 0 is still denied at the database.
        assertDenied("delayed old UPDATE resetting generation", () -> {
            try (Connection connection = old(); PreparedStatement update = connection.prepareStatement(
                    "UPDATE a2a_tasks SET task_data = ?, store_revision = NULL, migration_marker = NULL "
                            + "WHERE task_id = ?")) {
                update.setString(1, legacyJson);
                update.setString(2, legacyId);
                update.executeUpdate();
            }
        });
        assertEquals(migrated[0], rowByAdmin(legacyId)[0]);

        // Restart/stability: a fresh read sees the persisted baseline and migrating again examines nothing.
        TaskSnapshot again = store.readSnapshot(legacyId);
        assertEquals(1, again.task().generation());
        assertEquals(Long.valueOf(migrated[1]).longValue(), again.revision().value());
        assertEquals(0, migration.migrateAll());

        // New atomic writes under the new identity succeed and are stamped.
        String fresh = JpaAtomicTaskMutationTest.id();
        store.mutate(fresh, current -> JpaAtomicTaskMutationTest.plan(fresh, 1));
        String[] created = rowByAdmin(fresh);
        assertEquals("2", created[2]);
        assertEquals(JpaTaskMigration.BASELINE_MARKER, created[3]);
    }

    @Test
    @Order(2)
    void corruptMigratedRowIsNotReinitializedAndStaleNodeEpochIsRefused() throws Exception {
        String corrupt = JpaAtomicTaskMutationTest.id();
        String ok = JpaAtomicTaskMutationTest.id();
        String corruptDocument = JsonUtil.toJson(JpaTaskMigrationTest.legacyTask(corrupt));
        try (Connection operator = admin()) {
            // Tables may not exist yet when this test runs first; the other test's DDL is idempotent-guarded here.
            ensureUpgradedSchema(operator);
            try (PreparedStatement insert = operator.prepareStatement(
                    "INSERT INTO a2a_tasks (task_id, context_id, state, task_data, migration_marker, writer_epoch, "
                            + "store_revision) VALUES (?, ?, 'TASK_STATE_WORKING', ?, ?, 2, 5)")) {
                insert.setString(1, corrupt);
                insert.setString(2, "ctx-" + corrupt);
                insert.setString(3, corruptDocument);
                insert.setString(4, JpaTaskMigration.BASELINE_MARKER);
                insert.executeUpdate();
            }
        }
        migration.activate(2);
        String before = rowByAdmin(corrupt)[0];
        assertThrows(TaskSerializationException.class, () -> store.readSnapshot(corrupt));
        TaskMutationNotCommittedException rejected = assertThrows(TaskMutationNotCommittedException.class,
                () -> store.mutate(corrupt, current -> JpaAtomicTaskMutationTest.plan(corrupt, 2)));
        assertTrue(rejected.getCause() instanceof TaskSerializationException);
        assertEquals(before, rowByAdmin(corrupt)[0]);
        assertEquals("0", String.valueOf(JsonUtil.fromJson(before, Task.class).generation()));

        // A storage epoch ahead of this node's configured epoch refuses every write by this node.
        store.mutate(ok, current -> JpaAtomicTaskMutationTest.plan(ok, 1));
        try (Connection operator = admin(); Statement statement = operator.createStatement()) {
            statement.execute("UPDATE a2a_writer_epoch SET epoch = 3");
        }
        assertThrows(TaskMutationNotCommittedException.class,
                () -> store.mutate(ok, current -> JpaAtomicTaskMutationTest.plan(ok, 2)));
        assertNotEquals("3", rowByAdmin(ok)[2]);
    }

    /**
     * Why the docs require the old identity NOT to own the task tables: an owner keeps the right to GRANT itself
     * privileges back, so a REVOKE alone does not fence an owner. Uses a scratch table, never the fixture tables.
     */
    @Test
    @Order(3)
    void revokeDoesNotFenceAnOwnerWhoCanGrantItselfBack() throws Exception {
        try (Connection old = old(); Statement statement = old.createStatement()) {
            statement.execute("CREATE TABLE t09_owner_probe (id INTEGER PRIMARY KEY)");
        }
        try (Connection operator = admin()) {
            run(operator, List.of("REVOKE ALL ON t09_owner_probe FROM " + OLD));
        }
        assertDenied("owner INSERT after REVOKE (no GRANT-back yet)", () -> {
            try (Connection old = old(); Statement statement = old.createStatement()) {
                statement.execute("INSERT INTO t09_owner_probe VALUES (1)");
            }
        });
        try (Connection old = old(); Statement statement = old.createStatement()) {
            statement.execute("GRANT INSERT ON t09_owner_probe TO " + OLD);
            statement.execute("INSERT INTO t09_owner_probe VALUES (2)");
        }
        System.out.println("[T09 owner-probe] owner re-granted itself INSERT after REVOKE and wrote: "
                + "an owner is NOT fenced; transfer ownership away from the old identity");
    }

    private static void ensureUpgradedSchema(Connection operator) throws SQLException {
        try (Statement statement = operator.createStatement()) {
            statement.execute("ALTER TABLE a2a_tasks OWNER TO " + NEW);
            statement.execute("ALTER TABLE a2a_tasks ADD COLUMN IF NOT EXISTS store_revision BIGINT, "
                    + "ADD COLUMN IF NOT EXISTS writer_epoch BIGINT, "
                    + "ADD COLUMN IF NOT EXISTS migration_marker VARCHAR(255)");
            statement.execute("CREATE TABLE IF NOT EXISTS a2a_task_revision_counter "
                    + "(counter_id INTEGER PRIMARY KEY, next_block BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS a2a_writer_epoch "
                    + "(epoch_id INTEGER PRIMARY KEY, epoch BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS a2a_webhook_dispatch_lanes "
                    + "(task_id VARCHAR(255) PRIMARY KEY, writer_epoch BIGINT NOT NULL, next_generation BIGINT NOT NULL, "
                    + "holder_token VARCHAR(36), claimed_first_generation BIGINT, claimed_last_generation BIGINT)");
            statement.execute("CREATE TABLE IF NOT EXISTS a2a_webhook_dispatch_losses "
                    + "(loss_id VARCHAR(36) PRIMARY KEY, task_id VARCHAR(255) NOT NULL, first_generation BIGINT NOT NULL, "
                    + "last_generation BIGINT NOT NULL, recorded_at TIMESTAMP NOT NULL, reason VARCHAR(512) NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS a2a_webhook_dispatch_loss_task "
                    + "ON a2a_webhook_dispatch_losses (task_id, first_generation)");
            statement.execute("ALTER TABLE a2a_task_revision_counter OWNER TO " + NEW);
            statement.execute("ALTER TABLE a2a_writer_epoch OWNER TO " + NEW);
            statement.execute("ALTER TABLE a2a_webhook_dispatch_lanes OWNER TO " + NEW);
            statement.execute("ALTER TABLE a2a_webhook_dispatch_losses OWNER TO " + NEW);
        }
    }
}
