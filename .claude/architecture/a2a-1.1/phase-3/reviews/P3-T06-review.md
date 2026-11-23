# P3-T06 Independent Review: Enrollment Before Notification Eligibility

**Reviewer:** Bob (independent review in separate context)  
**Workspace:** `/Users/kabir/.codex-api/worktrees/3053/a2a-java`  
**Branch:** `support/a2a-1.1.0`  
**Base SHA:** `aa78cc26132d3823fc57cbf75af30cbf06093fa1`  
**Task:** P3-T06 "Enrollment before notification eligibility" (Issue #1303)  
**Deadline:** 2026-11-23 13:30 UTC  
**Verdict:** **Accepted within scope**

---

## 1. Candidate Artifacts & Hashes

- **Diff patch:** sha256 `febcc00c2834ca3fb9d218b130f45443e4b1447b11b9c1dea01066271b13a563`
  - `PushNotificationConfigStore.java`: additive `default boolean deleteInfoIfUnchanged(TaskPushNotificationConfig)`
  - `InMemoryPushNotificationConfigStore.java`: atomic exact-value `deleteInfoIfUnchanged` implementation
- **Untracked files:**
  - `server-common/src/main/java/org/a2aproject/sdk/server/tasks/TaskEnrollmentCoordinator.java` (sha256 `b8c98f7f2b6e4c3a2bfcc22c99cc114a099bd5b2120d77b7e6dbd7472d4fe714`)
  - `server-common/src/test/java/org/a2aproject/sdk/server/tasks/TaskEnrollmentCoordinatorTest.java` (sha256 `bbfa9f2ffcd883328837eee084dbfdf1e9a564ba302bc39e9fe3ce7435299110`)

---

## 2. Review Analysis & Findings

### 2.1 New-Task Provisional Enrollment Contract
- **Config-First & Read-After-Write Visibility:** `TaskEnrollmentCoordinator.enrollNewTask` invokes `configStore.setInfo` followed by `isVisible(taskId, configId)` pagination verification before task mutation. If the store write fails or is unreadable, it deletes its written config (if unchanged) and throws an error immediately before any task mutation occurs.
- **Definitive Non-Commit Cleanup:** On `taskNotCommitted()`, only the hidden new-task provisional config owned by the attempt is deleted using `deleteInfoIfUnchanged(written)`. A concurrent replacement with different properties is preserved. Swallowed exceptions are logged, leaving an invisible orphan tracked for bounded expiry.
- **Indeterminate Commit & Reconciliation:** On `outcomeUnknown()`, state transitions to `State.INDETERMINATE`. It is never deleted by `expireOrphans()`, honoring the rule that absence from one read is not proof of failure. `reconcileIndeterminate(taskExists)` stops tracking once the task exists.
- **Bounded Expiry:** `expireOrphans(taskExists)` cleans only expired `PROVISIONAL` entries whose task does not exist. Entries past expiry that cannot be deleted are removed from the attempts map so memory tracking stays bounded.

### 2.2 Existing-Task Independent-Store Contract
- **No Compensation / Survived Replacement:** `enrollExistingTask` returns an `Enrollment` with `newTask = false`. Calling `taskNotCommitted()` returns `false` and performs no delete or restore operations. Concurrent updates survive intact.
- **Partial-Effect Error Reporting:** `partialEffectError(Throwable, boolean)` preserves the original typed error (`TaskGenerationMismatchError`, `InvalidParamsError`, `TaskNotFoundError`, `InternalError`) while attaching standard details (`taskId`, `pushNotificationConfigId`, `pushNotificationConfigEnrolled=true`, `inputOutcome=not-committed|unknown`). No new protocol error kind or wire schema modification is introduced.

### 2.3 Eligibility & Visibility Seams
- **Push Notification Delivery:** `BasePushNotificationSender` only dispatches for committed task events / batches. Provisional configs for non-existent tasks cannot receive push events because no task event can be committed or dispatched prior to task creation.
- **Public API Visibility:** In `DefaultRequestHandler`, all public push notification configuration operations (`onCreateTaskPushNotificationConfig`, `onGetTaskPushNotificationConfig`, `onListTaskPushNotificationConfigs`, `onDeleteTaskPushNotificationConfig`) enforce `taskStore.get(taskId) != null` before reading or mutating the config store. Thus, provisional configs remain hidden while the task does not exist.
- **No-Task Direct Message Branch:** A no-task direct Message branch does not call `TaskEnrollmentCoordinator`, leaving no persisted config.

### 2.4 Thread Safety, SPI Compatibility & T07/T08 Consumability
- **Thread Safety:** `attempts` map is a `ConcurrentHashMap` with atomic operations (`put`, `remove(key, value)`, `computeIfPresent`). `InMemoryPushNotificationConfigStore.deleteInfoIfUnchanged` performs an atomic `computeIfPresent` on `pushNotificationInfos`.
- **SPI Compatibility:** `deleteInfoIfUnchanged` is added as a default method returning `false` on `PushNotificationConfigStore`, ensuring backward compatibility for custom store implementations.
- **Consumability by T07 / T08:** Provides the complete prepare/outcome/cleanup resource API (`enrollNewTask`, `enrollExistingTask`, `taskCommitted`, `taskNotCommitted`, `outcomeUnknown`, `partialEffectError`, `expireOrphans`, `reconcileIndeterminate`) ready for consumption in `DefaultRequestHandler` (T07) and JPA transactional / independent authority integration (T08).

---

## 3. Verification & Execution Evidence

Executed required test suite via the exact specified command:
```sh
/usr/bin/timeout -k 30s 1200s mvn -B -ntp -pl server-common -am \
  -Dtest=TaskEnrollmentCoordinatorTest,PushNotificationSenderTest,InMemoryPushNotificationConfigStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false \
  -DargLine=-javaagent:/Users/kabir/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.8/byte-buddy-agent-1.18.8.jar \
  test > /private/tmp/a2a-p3-t06-review-run.log 2>&1
```

**Results:**
- `TaskEnrollmentCoordinatorTest`: 15 runs, 0 failures, 0 errors, 0 skipped
- `PushNotificationSenderTest`: 41 runs, 0 failures, 0 errors, 0 skipped
- `InMemoryPushNotificationConfigStoreTest`: 35 runs, 0 failures, 0 errors, 0 skipped
- **Total:** 91 tests run, 0 failures, 0 errors, 0 skipped (Total time: 2.695 s, BUILD SUCCESS)
- **Log file:** `/private/tmp/a2a-p3-t06-review-run.log`

---

## 4. Limitations & Skips

- Docker / Testcontainers tests were not executed in this environment (Docker daemon unavailable).
- Separate RED compile run was not captured prior to implementation snapshot.
- DefaultRequestHandler wiring is scoped to P3-T07.
- JPA transactional push store adapter integration is scoped to P3-T08.
