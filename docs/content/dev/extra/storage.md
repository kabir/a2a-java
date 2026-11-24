---
title: Storage & Persistence
description: JPA-backed TaskStore and PushNotificationConfigStore for the A2A Java SDK, providing database persistence for production and load-balanced deployments.
layout: page
---

# Storage & Persistence

The default SDK uses in-memory stores that are lost on restart. For production deployments — especially load-balanced environments — replace them with JPA-backed stores that persist data to a relational database.

Both JPA stores use the Jakarta Persistence API (JPA 3.0+), making them suitable for any JPA provider and Jakarta EE application server. They share the same persistence unit name (`a2a-java`) and database configuration, so they can share a single datasource.

> **Note:** Stored objects are serialized to JSON according to the current A2A specification version. Future specification versions may change the format. These stores are intended for the operational lifetime of tasks, not long-term archival. If you wish to keep objects stored between protocol versions, you may have to implement migration of the stored data.

## JPA Task Store

Replaces `InMemoryTaskStore` with a database-backed implementation. The `JpaDatabaseTaskStore` is annotated to take precedence over the default — it is a drop-in replacement.

### 1. Add Dependency

```xml
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-extras-task-store-database-jpa</artifactId>
</dependency>
```

### 2. Configure Database

#### For Quarkus Reference Servers

Add to your `application.properties`:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/a2a_db
quarkus.datasource.username=your_username
quarkus.datasource.password=your_password
quarkus.hibernate-orm."a2a-java".datasource=<default>
quarkus.hibernate-orm."a2a-java".packages=org.a2aproject.sdk.extras.taskstore.database.jpa
# Application-managed production migrations; update is only for development
quarkus.hibernate-orm."a2a-java".database.generation=none
```

#### For WildFly/Jakarta EE Servers

> **Note:** On a Jakarta EE application server, the JPA dependencies are provided by the runtime. Mark them with `<scope>provided</scope>` in your POM to avoid packaging conflicts.

Create or update your `persistence.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
    <persistence-unit name="a2a-java" transaction-type="JTA">
        <jta-data-source>java:jboss/datasources/A2ADataSource</jta-data-source>

        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTask</class>
        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskOwner</class>
        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskRevisionCounter</class>
        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaWriterEpoch</class>
        <exclude-unlisted-classes>true</exclude-unlisted-classes>

        <properties>
            <property name="jakarta.persistence.schema-generation.database.action" value="none"/>
            <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
        </properties>
    </persistence-unit>
</persistence>
```

### 3. Database Schema

Schema creation is controlled by the application. Production upgrades require an application-managed migration for both task and ownership tables before using the updated store. Register `JpaTask`, `JpaTaskOwner`, `JpaTaskRevisionCounter` and `JpaWriterEpoch` in the same `a2a-java` unit; package scanning discovers them in Quarkus. The task schema includes:

```sql
CREATE TABLE a2a_tasks (
    task_id VARCHAR(255) PRIMARY KEY,
    context_id VARCHAR(255),
    state VARCHAR(255),
    status_timestamp TIMESTAMP,
    task_data TEXT NOT NULL,
    finalized_at TIMESTAMP,
    store_revision BIGINT,
    writer_epoch BIGINT,
    migration_marker VARCHAR(255)
);

CREATE TABLE a2a_task_revision_counter (
    counter_id INTEGER PRIMARY KEY,
    next_block BIGINT NOT NULL
);

CREATE TABLE a2a_writer_epoch (
    epoch_id INTEGER PRIMARY KEY,
    epoch BIGINT NOT NULL
);
```

`store_revision` is the storage revision of the row and is independent of the protocol generation inside `task_data`.
Each task takes a never-used revision block from `a2a_task_revision_counter` when it is created, so deleting and recreating a
task ID never reuses a revision. `store_revision`, `writer_epoch` and `migration_marker` are nullable: a row written by the
legacy `save` path receives its revision the first time it is read as a snapshot or mutated. `writer_epoch` and
`migration_marker` are written by the new store once a storage writer epoch is activated (see "Upgrading shared task
storage"); they are a cooperative check among new binaries only and are **not** a fence against older binaries. Register
`JpaTaskRevisionCounter` and `JpaWriterEpoch` in the same `a2a-java` unit; `JpaTaskRevisionAllocator` creates the single
counter row on first use.

`JpaDatabaseTaskStore` implements `AtomicTaskStore`: each mutation locks the task row, plans, persists and commits in one
transaction and is reported committed only after that transaction completes. A caller sharing the `a2a-java` unit with
the JPA push notification configuration store can use `mutateInTransaction` so that configuration and task input commit or
roll back together. Independent configuration stores are not all-or-nothing: an enrolled configuration stays visible if the
later task input fails.

### Upgrading shared task storage to A2A 1.1 (drain, fence, migrate)

This is a **coordinated drain-and-upgrade, not a rolling upgrade**: mixed old/new writers on shared task data are not
supported. This restriction concerns server binaries only; clients of all three protocol versions work against the
upgraded instance. Merely removing old nodes from HTTP routing is not a fence. A column or JSON marker that an old binary
ignores is not a fence either: exclusion of old writers must be enforced by the database. The schema version alone does
not make a deployment 1.1-capable. Run the procedure against a disposable copy first; the SDK never migrates data for you
at startup.

1. **Stop admission and drain.** Stop accepting requests, then stop or drain every old executor and every scheduled or
   background writer. In an embedded single-process deployment stop the old process: the new process needs exclusive
   storage ownership.
2. **Use distinct identities.** The new fleet connects with a database identity (and a broker identity, see
   [Replicated Queue Manager](replicated-queue-manager.md)) that the old binaries never used.
3. **Upgrade the schema** with the DDL above (new columns, `a2a_task_revision_counter`, `a2a_writer_epoch`) and
   **transfer ownership of every task table to the new identity** (or to a dedicated owner role that is neither the old nor
   the new application identity). The old identity must not own the tables, must not be a superuser and must not be a
   member of the owner role: an owner holds all privileges implicitly and can `GRANT` them back to itself after the
   `REVOKE`, which defeats the fence (`JpaWriterFencePostgresTest.revokeDoesNotFenceAnOwnerWhoCanGrantItselfBack` demonstrates this).
   Granting the new identity write privileges while the old identity stays owner is **not** a supported fence. Keep read
   access for the old identity only if you need it (`GRANT SELECT ... TO a2a_old`).
   `a2a_writer_epoch` and `a2a_task_revision_counter` must exist even in legacy mode: the new store reads the (empty)
   epoch table on every operation and refuses to run without it.
4. **Fence the old identity at the database**, then terminate its sessions. PostgreSQL example (the commands exercised by
   `JpaWriterFencePostgresTest`):

   ```sql
   REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON a2a_tasks, a2a_task_owners FROM a2a_old;
   REVOKE ALL ON a2a_task_revision_counter, a2a_writer_epoch FROM a2a_old;
   SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = 'a2a_old';
   ```

   Verify enforcement before continuing: an `INSERT`, `UPDATE` and `DELETE` as the old identity must fail with
   `permission denied` (SQLSTATE `42501`). If you cannot demonstrate this, do not migrate.
5. **Activate the writer epoch and migrate.** Configure every new node with `a2a.task-store.writer-epoch=<n>` and call
   `JpaTaskMigration.activate(n)` once, then `JpaTaskMigration.migrateAll()` (idempotent; it must complete before traffic is served, see step 6). Until a storage epoch is activated the store stays in legacy mode and neither stamps nor baselines rows.
   Epochs only increase. After activation a node whose configured epoch differs from the stored epoch refuses every write,
   delete and baseline.
6. **Complete `migrateAll()` before serving traffic.** Lazy baselining happens only on the atomic paths (snapshot read and
   mutation). `get` and `list` return the stored Task unchanged, so until a task is migrated a read through them can still
   show generation `0` while the atomic path shows generation `1`.
7. **Start the new fleet.** The epoch check among new nodes is best-effort: it takes no lock on the epoch row, so a write
   that already passed the check can still commit just after `activate`. Exclusion of old writers comes from the database
   fence in step 4, not from the epoch.

Each legacy task is baselined exactly once, under its row lock: generation `0` becomes generation `1`; history, artifacts
(including absent artifact roles, which keep their legacy agent meaning) and delivery metadata are kept unchanged, and no
earlier timeline or chunk order is invented. The row's `migration_marker` and `writer_epoch` are stored outside the Task
JSON. Restart keeps the persisted baseline. A marked row whose stored Task has no canonical generation is reported as
corrupt (`TaskSerializationException`) and is never re-initialized to generation 1.

**Rollback.** There is no in-place downgrade. Rolling back to an old binary requires an outage and restoring a consistent
pre-upgrade snapshot of the task and push-configuration tables together with a compatible broker boundary; every change
made after the upgrade is lost. Never point an old binary at migrated data or erase `migration_marker` to make it start.

**Not provided.** In-memory storage has no persisted baseline: tasks are lost on restart, and a task written through the
legacy `save` path is stabilized at generation 1 only for the lifetime of that process.

### 4. Ownership schema and authorization selection

PostgreSQL 16 ownership DDL:

```sql
CREATE TABLE a2a_task_owners (
    task_id VARCHAR(255) PRIMARY KEY,
    subject_key BYTEA NOT NULL
);
CREATE INDEX a2a_task_owner_subject ON a2a_task_owners (subject_key, task_id);
```

MySQL 8 ownership DDL:

```sql
CREATE TABLE a2a_task_owners (
    task_id VARCHAR(255) PRIMARY KEY,
    subject_key VARBINARY(1024) NOT NULL
);
CREATE INDEX a2a_task_owner_subject ON a2a_task_owners (subject_key, task_id);
```

Verify the actual binary type and complete index. PostgreSQL must use `BYTEA`, not a large-object OID. MySQL requires variable-length `VARBINARY(1024)`, not padded `BINARY`, text with a binary collation, or a prefix index. Other databases need an equivalent exact variable-length binary mapping.

The SDK uses strict UTF-8 and rejects malformed surrogates or subjects exceeding 1,024 encoded bytes before ownership access. Case, accents, Unicode-normalization and trailing-space distinctions remain significant; there is no normalization, trimming, truncation, padding or lossy hashing. Backfill only from a trusted source preserving original subject keys. Audit legacy text-collation associations and lossless conversion; a migration cannot recover distinctions already discarded.

Adding the module selects the task store but does not enable an authorization provider. Explicitly select the supplied owner-only provider in Quarkus:

```properties
quarkus.arc.selected-alternatives=org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskAuthorizationProvider
```

For portable CDI, expose an ordinary application producer instead of relying on archive-scoped `beans.xml` alternative selection:

```java
@ApplicationScoped
public class TaskAuthorizationConfiguration {
    @Produces
    @Dependent
    TaskAuthorizationProvider taskAuthorization(JpaTaskOwnershipRepository ownership) {
        return new JpaTaskAuthorizationProvider(ownership);
    }
}
```

Use the Jakarta CDI annotations and the JPA module's provider/repository types. Choose the producer or Quarkus alternative selection, avoiding ambiguous providers. The repository uses an intercepted JTA helper: insertion and duplicate confirmation each run in a separate `REQUIRES_NEW` transaction with a fresh persistence context. Confirmation starts after insertion rollback, so a stale repeatable-read caller cannot hide committed ownership or become rollback-only. Outside CDI, use `JpaTaskOwnershipRepository.create(entityManagerFactory, transactionManager)` and pass `new JpaTaskAuthorizationProvider(repository)` to `DefaultRequestHandler.builder().authorizationProvider(provider)`. The factory must be JTA-enabled for the same database/transaction manager. Do not directly instantiate the transaction helper.

Authenticate `ServerCallContext.getUser()` and supply a username unique across your identity domain. Multiple issuers or tenant-local names require stable namespaced keys or a custom policy. Anonymous users and unknown ownership are denied; recording is first-writer-wins across replicas. An owner row may precede its task, so there is no immediate task foreign key. A persisted task remains inaccessible until ownership is recorded. Store deletion removes ownership in the same transaction; direct database maintenance must clean up both tables. Replicas must share ownership storage.

With no provider, authorization is required by default. Only deliberate providerless deployments should set `a2a.authorization.required=false`. Configured providers remain authoritative with that setting. An explicitly configured allow-all provider may expose `JpaTaskReadScope.All`, including unowned tasks, if its individual checks agree.

A supported `JpaTaskAuthorizationCapability` restricts both database count and page queries. The exact authorized total excludes the cursor and is repeated on every page; the page query alone uses the cursor. The handler's list gate and final checks remain. Counts and pages follow configured database isolation, without an implied stronger snapshot guarantee.

Unsupported providers retain iterative checks in Java. Their exact count may fail at `a2a.max-scanned-tasks`; `a2a.authorization-scan-batch-size` controls fetch size. These limits do not apply to capability-based queries. Lookup or capability exceptions fail the request rather than switching policy. See [Task Authorization](../authorization.md) for capability contracts and [Configuration](../configuration.md) for fallback limits.

### Persistence Unit Name

The module uses the persistence unit name `"a2a-java"`. Ensure your `persistence.xml` defines a persistence unit with this name.

## JPA Push Notification Config Store

Replaces `InMemoryPushNotificationConfigStore` with a database-backed implementation, ensuring push notification subscriptions survive application restarts. The `JpaDatabasePushNotificationConfigStore` is annotated to take precedence over the default — it is a drop-in replacement.

### 1. Add Dependency

```xml
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-extras-push-notification-config-store-database-jpa</artifactId>
</dependency>
```

### 2. Configure Database

Uses the same datasource and persistence unit (`a2a-java`) as the JPA Task Store.

#### For Quarkus Reference Servers

The same `application.properties` configuration as the JPA Task Store applies.

#### For WildFly/Jakarta EE Servers

Create or update your `persistence.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
    <persistence-unit name="a2a-java" transaction-type="JTA">
        <jta-data-source>java:jboss/datasources/A2ADataSource</jta-data-source>

        <class>org.a2aproject.sdk.extras.pushnotificationconfigstore.database.jpa.JpaPushNotificationConfig</class>
        <exclude-unlisted-classes>true</exclude-unlisted-classes>

        <properties>
            <property name="jakarta.persistence.schema-generation.database.action" value="none"/>
            <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
        </properties>
    </persistence-unit>
</persistence>
```

### 3. Database Schema

When application schema generation is enabled, it creates a table with a composite primary key. Otherwise apply the corresponding migration:

```sql
CREATE TABLE a2a_push_notification_configs (
    task_id VARCHAR(255) NOT NULL,
    config_id VARCHAR(255) NOT NULL,
    task_data TEXT NOT NULL,
    protocol_version VARCHAR(255),
    created_at TIMESTAMP,
    PRIMARY KEY (task_id, config_id)
);
```

## Using Both Stores Together

When using both JPA stores, combine their entity classes in a single `persistence.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.0">
    <persistence-unit name="a2a-java" transaction-type="JTA">
        <jta-data-source>java:jboss/datasources/A2ADataSource</jta-data-source>

        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTask</class>
        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskOwner</class>
        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskRevisionCounter</class>
        <class>org.a2aproject.sdk.extras.taskstore.database.jpa.JpaWriterEpoch</class>
        <class>org.a2aproject.sdk.extras.pushnotificationconfigstore.database.jpa.JpaPushNotificationConfig</class>
        <exclude-unlisted-classes>true</exclude-unlisted-classes>

        <properties>
            <property name="jakarta.persistence.schema-generation.database.action" value="none"/>
            <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
        </properties>
    </persistence-unit>
</persistence>
```

For Quarkus, include both store packages in the named persistence unit:

```properties
quarkus.hibernate-orm."a2a-java".packages=org.a2aproject.sdk.extras.taskstore.database.jpa,org.a2aproject.sdk.extras.pushnotificationconfigstore.database.jpa
```

This discovers `JpaTask`, `JpaTaskOwner`, `JpaTaskRevisionCounter` and `JpaPushNotificationConfig`. Schema generation remains an application choice; production upgrades require migrations.
