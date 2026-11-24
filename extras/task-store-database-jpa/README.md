# A2A Java SDK - JPA Database TaskStore

This module provides a JPA-based implementation of the `TaskStore` interface that persists tasks to a relational database instead of keeping them in memory.

The persistence is done with the Jakarta Persistence API, so this should be suitable for any JPA 3.0+ provider and Jakarta EE application server.


> **NOTE:**
>
> The `Task` instances stored in the JPA Database `TaskStore` are simply serialized to JSON before storing in the database, and serialized from JSON when loading from the database. This is done according to the current A2A specification version's format of `Task`.
>
> Future versions of the specification might use a different format. This is not intended to be a long-term store; its usage is meant to be for the lifetime of the `Task` in order to have access to the `Task` when running in a load-balanced environment.
>
> If you wish to keep the `Task` instances stored between protocol versions, you might have to implement some migration of the stored data.


## Quick Start

### 1. Add Dependency

Add this module to your project's `pom.xml`:

```xml
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-extras-task-store-database-jpa</artifactId>
    <version>${a2a.version}</version>
</dependency>
```

The `JpaDatabaseTaskStore` is annotated in such a way that it should take precedence over the default `InMemoryTaskStore`. Hence, it is a drop-in replacement. 

### 2. Configure Database

The following examples assume you are using PostgreSQL as your database. To use another database, adjust as needed for your environment.

#### For Quarkus Reference Servers

Add to your `application.properties`:

```properties
# Database configuration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/a2a_db
quarkus.datasource.username=your_username
quarkus.datasource.password=your_password

# Hibernate configuration
# Named unit; schema generation is an application choice
quarkus.hibernate-orm."a2a-java".datasource=<default>
quarkus.hibernate-orm."a2a-java".packages=org.a2aproject.sdk.extras.taskstore.database.jpa
quarkus.hibernate-orm."a2a-java".database.generation=none
```

#### For WildFly/Jakarta EE Servers

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
            <!-- Change as required for your environment -->
            <property name="jakarta.persistence.schema-generation.database.action" value="none"/>
            <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
        </properties>
    </persistence-unit>
</persistence>
```

### 3. Schema and production migrations

The application controls schema creation. Register `JpaTask`, `JpaTaskOwner`, `JpaTaskRevisionCounter` and `JpaWriterEpoch` in the same `a2a-java` JTA persistence unit, including when unlisted classes are excluded. The Quarkus package setting above discovers both. Development schema generation creates the ownership entity even without selecting an authorization provider. Production upgrades require an application-managed migration before using the updated store, whose deletion path also cleans up ownership.

The task schema includes all of these columns. Use the database dialect's timestamp representation for Java `Instant`:

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

`subject_key` contains the exact UTF-8 encoding of the authenticated username. Malformed surrogates and identities exceeding **1,024 encoded bytes** are rejected before ownership access. The SDK does not normalize Unicode, change case, strip accents, trim spaces, truncate, pad, or replace identities with hashes. Case, accent, Unicode-normalization, and trailing-space distinctions all remain significant.

Verify the actual column and complete `(subject_key, task_id)` index. PostgreSQL must use `BYTEA`, not a large-object OID. MySQL must use `VARBINARY(1024)`, not padded `BINARY`, a character column with a binary collation, or a prefix index. Other databases need an equivalent variable-length binary type with exact byte comparison; encoding in Java alone cannot fix a lossy schema.

Backfill legacy tasks only from a trusted source preserving original subject keys. Audit any earlier case/accent/space-insensitive associations and validate lossless text-to-binary conversion. Changing the column cannot reconstruct identity distinctions already discarded. Migration tools must respect the byte limit without normalization or truncation. Legacy tasks without ownership remain denied.

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

### 4. Explicitly select authorization

Adding this module selects the task store, **not an authorization provider**. With no provider, task operations are denied by default. Deliberately providerless deployments may set `a2a.authorization.required=false`, or use the handler builder's `authorizationRequired(false)`. A configured provider remains authoritative even when that flag is false.

For Quarkus, select the supplied alternative explicitly:

```properties
quarkus.arc.selected-alternatives=org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskAuthorizationProvider
```

For portable CDI, expose an ordinary application producer. Selecting an alternative in the application's `beans.xml` is archive-scoped and may not enable it for injection points in SDK archives.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskAuthorizationProvider;
import org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskOwnershipRepository;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;

@ApplicationScoped
public class TaskAuthorizationConfiguration {
    @Produces
    @Dependent
    TaskAuthorizationProvider taskAuthorization(JpaTaskOwnershipRepository ownership) {
        return new JpaTaskAuthorizationProvider(ownership);
    }
}
```

The injected repository invokes `JpaTaskOwnershipTransactions` through CDI/JTA interception. Configure a transaction-scoped persistence context and JTA manager. Do not construct the transaction helper directly; use the factory below outside CDI. Choose either alternative selection or the producer to avoid ambiguous providers.

For programmatic handler construction, use the application's JTA entity-manager factory and transaction manager:

```java
var ownership = JpaTaskOwnershipRepository.create(entityManagerFactory, transactionManager);
var authorization = new JpaTaskAuthorizationProvider(ownership);
var handler = DefaultRequestHandler.builder()
        .agentExecutor(agentExecutor)
        .taskStore(taskStore)
        .authorizationProvider(authorization)
        .build();
```

The factory requires a JTA factory for `a2a-java` and the manager governing its transactions. Insertion suspends the caller and uses a fresh persistence context in a new transaction. On failure, insertion rolls back before a **second new transaction** confirms committed ownership. This preserves first-writer-wins without poisoning unrelated caller work or consulting its stale repeatable-read snapshot. Failures propagate if committed ownership cannot be confirmed.

Provide an authenticated `User` in `ServerCallContext`. Its username must uniquely identify the subject across your deployment. Multiple issuers or tenant-local usernames require stable namespaced identities or a custom provider. Anonymous users and unknown ownership are denied. Creation requires authentication; recording is idempotent and never transfers an existing owner.

An ownership row may precede its task on asynchronous paths, so no immediate task foreign key is required. Access remains denied between task persistence and ownership recording. Replicas must use the same ownership database; recording or replication delays never grant access. Deleting through `JpaDatabaseTaskStore` removes ownership in the same transaction, even when the task row is absent. Direct maintenance must clean up both tables.

## Listing and capabilities

The core `TaskAuthorizationProvider.getCapability(Class<T>)` defaults to `Optional.empty()` and has no JPA dependency. The configured provider may expose `JpaTaskAuthorizationCapability.readScope(context, operation)`, returning `JpaTaskReadScope.OwnedBy(subject)`, `All`, or `None`.

For a supported capability the store resolves `LIST_TASKS` scope once and applies the same restriction to database `COUNT` and paginated `SELECT`. The exact authorized total covers every matching page; the cursor restricts only the page query. Owner scopes use correlated ownership `EXISTS` and binary parameters. `All` includes unowned tasks; `None` returns no tasks. The handler's unconditional list gate and final task checks remain enforced. Two statements do not guarantee a stronger snapshot than the configured database isolation.

Custom allow-all providers may expose `All` only when their individual checks agree. Capabilities must represent the provider's **complete operation-specific read policy**. Wrappers adding restrictions must not forward a broader capability. Unsupported capabilities use fallback; lookup or execution exceptions fail the request without changing policy.

The JPA fallback checks candidates in Java to obtain an exact count. `a2a.max-scanned-tasks` (default 10,000) bounds this scan; `a2a.authorization-scan-batch-size` (default 100) controls fetch size. Both must be positive. Exceeding the ceiling fails explicitly instead of returning a partial total. These settings apply **only to fallback**, not optimized queries, which still incur database execution cost.

Generic stores inheriting `TaskStore.listAuthorized` have separate fallback limits: 20,000 distinct candidates per phase, 300 count queries, 300 normal page/lookahead queries, and up to 100 additional queries only for opaque token recovery within the fetched final batch. Unsupported policies over larger datasets can still fail; matching backend capabilities enable database filtering and counting.

## Tests

H2 tests provide fast coverage. Real PostgreSQL 16 and MySQL 8 tests verify the actual binary column/full index, identity isolation, and duplicate-ownership recovery. PostgreSQL covers default Read Committed and explicit Repeatable Read; MySQL adds default text-collation and Repeatable Read coverage. MySQL support does not change the cloud example's PostgreSQL database.

With a working Docker/Podman engine:

```bash
mvn -pl server-common,extras/task-store-database-jpa -am test
```

`-DskipDockerTests=true` is an explicit local skip. Skipped database tests do not validate identity equality or transaction recovery.
