package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskGenerationMismatchError;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TimelineEntry;
import org.a2aproject.sdk.testutils.docker.RequiresDocker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 3 (T13) evidence that the selected shared-authority profile (PostgreSQL 16 + Kafka 4.2.0 SASL/ACL broker +
 * two live Quarkus processes, one writer epoch) keeps its ordering and fencing claims. Only internal fixture barriers
 * are used: the nodes' own publisher, lane store, HTTP client and executor decorators, plus row locks taken in the
 * real database. No mocks, no embedded database, no skipped cases.
 */
@Testcontainers
@RequiresDocker
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase3AuthorityMultiInstanceTest {

    private static final String PUBLISH = "publish";
    private static final String WEBHOOK_HTTP = "webhook-http";
    private static final String LANE_CLAIM = "lane-claim";
    private static final String EXECUTOR = "executor";

    private static Phase3Cluster cluster;

    @BeforeAll
    static void setup() throws Exception {
        cluster = new Phase3Cluster();
        cluster.start();
    }

    @AfterAll
    static void teardown() {
        if (cluster != null) {
            cluster.stop();
        }
    }

    // ---- helpers ----

    /** Outcome of one input: the committed Task it returned, or the typed failure. */
    record Outcome(String node, Task task, Throwable failure) {
        boolean accepted() {
            return failure == null;
        }
    }

    private static Message text(String text, Task task) {
        Message.Builder builder = Message.builder(A2A.toUserMessage(text));
        if (task != null) {
            builder.taskId(task.id()).contextId(task.contextId());
        }
        return builder.build();
    }

    private static Outcome send(Phase3Cluster.Node node, String text, Task task, Long ifGeneration) {
        CompletableFuture<Outcome> done = new CompletableFuture<>();
        List<Task> seen = new CopyOnWriteArrayList<>();
        try (Client client = node.client()) {
            SendInputParams.Builder params = SendInputParams.builder().message(text(text, task));
            if (ifGeneration != null) {
                params.configuration(MessageSendConfiguration.builder().ifGenerationMatch(ifGeneration).build());
            }
            client.sendInput(params.build(), List.of((ClientEvent event, org.a2aproject.sdk.spec.AgentCard card) -> {
                if (event instanceof TaskEvent taskEvent) {
                    seen.add(taskEvent.getTask());
                } else if (event instanceof TaskUpdateEvent update) {
                    seen.add(update.getTask());
                }
            }), error -> done.complete(new Outcome(node.name, seen.isEmpty() ? null : seen.get(seen.size() - 1),
                    error)), null);
            return done.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new Outcome(node.name, null, e);
        }
    }

    private static Task get(Phase3Cluster.Node node, String taskId) throws Exception {
        try (Client client = node.client()) {
            return client.getTask(new TaskQueryParams(taskId), null);
        }
    }

    private static List<Long> timelineGenerations(Task task) {
        List<Long> out = new ArrayList<>();
        if (task.timeline() != null) {
            for (TimelineEntry entry : task.timeline()) {
                out.add(entry.generation());
            }
        }
        return out;
    }

    private static void register(Phase3Cluster.Node node, Task task, String hookName) throws Exception {
        try (Client client = node.client()) {
            client.createTaskPushNotificationConfiguration(TaskPushNotificationConfig.builder()
                    .taskId(task.id()).url(cluster.receiver.urlFor(hookName)).id(hookName).build(), null);
        }
    }

    private static String describe(Task task) {
        return "gen=" + task.generation() + " state=" + task.status().state() + " timeline="
                + timelineGenerations(task);
    }

    // ---- tests ----

    @Test
    @Order(1)
    void bothLiveNodesShareOneDatabaseAndOneEpoch() throws Exception {
        JsonObject node1 = cluster.app1.nodeInfo();
        JsonObject node2 = cluster.app2.nodeInfo();
        System.out.println("PHASE3-EVIDENCE node1=" + node1);
        System.out.println("PHASE3-EVIDENCE node2=" + node2);
        System.out.println("PHASE3-EVIDENCE postgres=" + cluster.postgresVersion());
        System.out.println("PHASE3-EVIDENCE kafka=" + cluster.kafkaCli("kafka-topics.sh", "--version"));
        assertEquals("app1", node1.get("nodeId").getAsString());
        assertEquals("app2", node2.get("nodeId").getAsString());
        assertNotEquals(node1.get("bootId").getAsString(), node2.get("bootId").getAsString());
        assertNotEquals(node1.get("pid").getAsString(), node2.get("pid").getAsString());
        assertEquals(1L, node1.get("nodeEpoch").getAsLong());
        assertEquals(1L, node2.get("nodeEpoch").getAsLong());
        assertEquals(1L, node1.get("storedEpoch").getAsLong());
        assertEquals(1L, cluster.storedEpoch());
        assertTrue(cluster.postgresVersion().contains("PostgreSQL 16"), cluster.postgresVersion());
    }

    // ---- shared evidence helpers ----

    /** A live streaming subscription whose observed event generations are recorded in arrival order. */
    static final class Watcher implements AutoCloseable {
        final String node;
        final List<Long> generations = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        volatile boolean snapshotSeen;
        private Client client;

        Watcher(Phase3Cluster.Node node, String taskId) throws Exception {
            this.node = node.name;
            client = node.streamingClient();
            client.subscribeToTask(new TaskIdParams(taskId), List.of((event, card) -> {
                Long generation = generationOf(event);
                if (event instanceof TaskEvent) {
                    snapshotSeen = true;
                }
                if (generation != null) {
                    generations.add(generation);
                }
            }), errors::add, null);
            await().atMost(Duration.ofSeconds(20)).until(() -> snapshotSeen || !errors.isEmpty());
            assertTrue(errors.isEmpty(), "subscription on " + node.name + " failed: " + errors);
        }

        @Override
        public void close() {
            try {
                client.close();
            } catch (Exception ignored) {
                // best effort
            }
        }

        boolean strictlyIncreasing() {
            for (int i = 1; i < generations.size(); i++) {
                if (generations.get(i) <= generations.get(i - 1)) {
                    return false;
                }
            }
            return true;
        }
    }

    static Long generationOf(ClientEvent event) {
        if (event instanceof TaskEvent taskEvent) {
            return taskEvent.getTask().generation();
        }
        if (event instanceof TaskUpdateEvent update) {
            if (update.getUpdateEvent() instanceof TaskStatusUpdateEvent status) {
                return status.generation();
            }
            if (update.getUpdateEvent() instanceof TaskArtifactUpdateEvent artifact) {
                return artifact.generation();
            }
            if (update.getUpdateEvent() instanceof TaskMessageUpdateEvent message) {
                return message.generation();
            }
        }
        return null;
    }

    private static final Pattern GENS = Pattern.compile("gens=([0-9,]*)");

    /** HTTP attempt starts (as recorded by the node itself, not the receiver) for one webhook name, in node order. */
    private static List<List<Long>> attemptStarts(Phase3Cluster.Node node, String hook) throws Exception {
        List<List<Long>> out = new ArrayList<>();
        for (JsonObject record : node.trace("/hook/" + hook, "http-attempt-start")) {
            Matcher matcher = GENS.matcher(record.get("detail").getAsString());
            List<Long> gens = new ArrayList<>();
            if (matcher.find() && !matcher.group(1).isEmpty()) {
                for (String part : matcher.group(1).split(",")) {
                    gens.add(Long.valueOf(part));
                }
            }
            out.add(gens);
        }
        return out;
    }

    private static List<Long> receiverGenerations(String hook) {
        List<Long> out = new ArrayList<>();
        for (Phase3Cluster.Receiver.Arrival arrival : cluster.receiver.arrivalsFor(hook)) {
            Matcher matcher = Pattern.compile("\"generation\"\\s*:\\s*\"?(\\d+)\"?").matcher(arrival.body());
            while (matcher.find()) {
                out.add(Long.valueOf(matcher.group(1)));
            }
        }
        return out;
    }

    private static void holdRowLockUntil(String taskId, Runnable whileHeld) throws Exception {
        try (Connection connection = DriverManager.getConnection(cluster.postgres.getJdbcUrl(),
                cluster.postgres.getUsername(), cluster.postgres.getPassword())) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT task_id FROM a2a_tasks WHERE task_id = ? FOR UPDATE")) {
                lock.setString(1, taskId);
                lock.executeQuery().close();
                whileHeld.run();
            } finally {
                connection.commit();
            }
        }
    }

    // ---- scenarios ----

    @Test
    @Order(2)
    void competingGuardedWritersOnDifferentNodesAcceptExactlyOne() throws Exception {
        Outcome created = send(cluster.app1, "open race", null, null);
        assertTrue(created.accepted(), String.valueOf(created.failure()));
        Task task = created.task();
        long base = get(cluster.app2, task.id()).generation();
        register(cluster.app2, task, "race");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<CompletableFuture<Outcome>> writers = new ArrayList<>();
        try {
            // Barrier: a row lock held in the real database holds both nodes inside their mutation until both are
            // verifiably blocked on the same task row; releasing it lets the database serialize the guard checks.
            holdRowLockUntil(task.id(), () -> {
                writers.add(CompletableFuture.supplyAsync(() -> send(cluster.app1, "race-from-app1", task, base), pool));
                writers.add(CompletableFuture.supplyAsync(() -> send(cluster.app2, "race-from-app2", task, base), pool));
                await().atMost(Duration.ofSeconds(30)).until(() -> {
                    int blocked = cluster.query("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'",
                            rs -> {
                                rs.next();
                                return rs.getInt(1);
                            });
                    return blocked >= 2;
                });
                System.out.println("PHASE3-EVIDENCE race both writers blocked on the task row lock (pg_stat_activity)");
            });
            List<Outcome> outcomes = new ArrayList<>();
            for (CompletableFuture<Outcome> writer : writers) {
                outcomes.add(writer.get(60, TimeUnit.SECONDS));
            }
            List<Outcome> accepted = outcomes.stream().filter(Outcome::accepted).toList();
            List<Outcome> rejected = outcomes.stream().filter(o -> !o.accepted()).toList();
            outcomes.forEach(o -> System.out.println("PHASE3-EVIDENCE race outcome node=" + o.node() + " accepted="
                    + o.accepted() + " failure=" + o.failure()));
            assertEquals(1, accepted.size(), "exactly one guarded writer is accepted: " + outcomes);
            assertEquals(1, rejected.size());
            assertTrue(rejected.get(0).failure() instanceof TaskGenerationMismatchError,
                    "the loser gets the typed generation mismatch, not a transport error: " + rejected.get(0).failure());
            String winnerTag = "race-from-" + accepted.get(0).node();
            String loserTag = "race-from-" + rejected.get(0).node();

            Task finalTask = get(cluster.app1, task.id());
            System.out.println("PHASE3-EVIDENCE race final " + describe(finalTask));
            List<Long> timeline = timelineGenerations(finalTask);
            assertEquals(timeline.size(), timeline.stream().distinct().count(), "one durable sequence per mutation");
            for (int i = 1; i < timeline.size(); i++) {
                assertEquals(timeline.get(i - 1) + 1, timeline.get(i), "contiguous canonical sequence");
            }
            String history = String.valueOf(finalTask.history());
            assertEquals(1, history.split(winnerTag, -1).length - 1, "winner's message committed once");
            assertFalse(history.contains(loserTag), "loser's message was not committed (no partial mutation)");

            // Webhook eligibility: every committed generation after the base has exactly one HTTP attempt start
            // (recorded by the nodes) and one receiver arrival; none carries the loser.
            long last = finalTask.generation();
            await().atMost(Duration.ofSeconds(30)).until(() -> receiverGenerations("race").stream().distinct().count()
                    >= last - base);
            List<Long> starts = new ArrayList<>();
            attemptStarts(cluster.app1, "race").forEach(starts::addAll);
            attemptStarts(cluster.app2, "race").forEach(starts::addAll);
            System.out.println("PHASE3-EVIDENCE race attempt-start generations=" + starts + " receiver="
                    + receiverGenerations("race") + " lane=" + cluster.laneRow(task.id()));
            assertEquals(starts.stream().distinct().count(), starts.size(), "no generation is attempted twice");
            assertEquals(receiverGenerations("race").stream().distinct().count(), receiverGenerations("race").size());
            assertTrue(cluster.receiver.arrivalsFor("race").stream().noneMatch(a -> a.body().contains(loserTag)));
            assertTrue(cluster.laneRow(task.id()).contains("next_generation=" + (last + 1)),
                    cluster.laneRow(task.id()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(3)
    void pausedPublisherNeverLetsSubscribersSeeReversedGenerations() throws Exception {
        Outcome created = send(cluster.app1, "open pause", null, null);
        assertTrue(created.accepted(), String.valueOf(created.failure()));
        Task task = created.task();
        try (Watcher onA = new Watcher(cluster.app1, task.id()); Watcher onB = new Watcher(cluster.app2, task.id())) {
            cluster.app1.arm(PUBLISH, task.id());
            ExecutorService pool = Executors.newSingleThreadExecutor();
            CompletableFuture<Outcome> held = CompletableFuture.supplyAsync(
                    () -> send(cluster.app1, "pause-from-A", task, null), pool);
            await().atMost(Duration.ofSeconds(30)).until(() -> cluster.app1.waiting(PUBLISH) >= 1);
            System.out.println("PHASE3-EVIDENCE pause: node A committed and is held before publishing: "
                    + cluster.app1.trace(task.id(), "publish-before", "barrier-waiting"));
            long committedByA = get(cluster.app2, task.id()).generation();

            // Node B commits the next generation(s) while A's publisher is held.
            Outcome second = send(cluster.app2, "pause-from-B", get(cluster.app2, task.id()), committedByA);
            assertTrue(second.accepted(), String.valueOf(second.failure()));
            long committedByB = get(cluster.app1, task.id()).generation();
            assertTrue(committedByB > committedByA, "B committed beyond A's unpublished generation");
            // B's own observers see B's commit; A's observers see nothing newer while A's publication is held.
            await().atMost(Duration.ofSeconds(20)).until(() -> onB.generations.contains(committedByB));
            Thread.sleep(3000);
            long subscribedAt = onA.generations.get(0);
            assertTrue(onA.generations.stream().allMatch(g -> g <= subscribedAt),
                    "no later generation becomes visible on A while its earlier batch is still being published: "
                            + onA.generations);
            System.out.println("PHASE3-EVIDENCE pause: while A is held A subscriber=" + onA.generations
                    + " B subscriber=" + onB.generations);

            cluster.app1.release(PUBLISH);
            held.get(60, TimeUnit.SECONDS);
            pool.shutdownNow();
            await().atMost(Duration.ofSeconds(20)).until(() -> onA.generations.contains(committedByB));
            Thread.sleep(3000);
            System.out.println("PHASE3-EVIDENCE pause: A trace=" + cluster.app1.trace(task.id()));
            System.out.println("PHASE3-EVIDENCE pause: B trace=" + cluster.app2.trace(task.id()));
            System.out.println("PHASE3-EVIDENCE pause: A subscriber generations=" + onA.generations + " B subscriber="
                    + onB.generations + " errors A=" + onA.errors + " B=" + onB.errors);
            assertTrue(onA.strictlyIncreasing(), "subscriber on A saw reversed generations: " + onA.generations);
            assertTrue(onB.strictlyIncreasing(), "subscriber on B saw reversed generations: " + onB.generations);
            assertTrue(onA.errors.isEmpty() && onB.errors.isEmpty());
        }
    }

    // ---- scenario helpers ----

    private static List<String> topicLinesFor(String taskId) throws Exception {
        return cluster.readTopic().stream().filter(line -> line.contains(taskId)).toList();
    }

    private static int countTraces(Phase3Cluster.Node node, String fragment, String kind) throws Exception {
        return node.trace(fragment, kind).size();
    }

    /** HTTP attempt starts of both nodes for one webhook name, merged by wall-clock time, as generation lists. */
    private static List<Long> orderedAttemptGenerations(String hook) throws Exception {
        List<JsonObject> all = new ArrayList<>();
        all.addAll(cluster.app1.trace("/hook/" + hook, "http-attempt-start"));
        all.addAll(cluster.app2.trace("/hook/" + hook, "http-attempt-start"));
        all.sort(Comparator.comparingLong(record -> record.get("millis").getAsLong()));
        List<Long> out = new ArrayList<>();
        for (JsonObject record : all) {
            Matcher matcher = GENS.matcher(record.get("detail").getAsString());
            if (matcher.find() && !matcher.group(1).isEmpty()) {
                for (String part : matcher.group(1).split(",")) {
                    out.add(Long.valueOf(part));
                }
            }
        }
        return out;
    }

    private static boolean nonDecreasing(List<Long> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) < values.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    private static String tail(String logs, String needle) {
        StringBuilder out = new StringBuilder();
        for (String line : logs.split("\n")) {
            if (line.contains(needle) && (line.contains(" WARN ") || line.contains(" ERROR "))) {
                String stripped = line.strip();
                out.append(stripped, 0, Math.min(stripped.length(), 260)).append(" | ");
            }
        }
        return out.toString();
    }

    private static Task get0(Phase3Cluster.Node node, String taskId) {
        try {
            return get(node, taskId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- duplicate / reordered broker records ----

    // Claim limit: the replayed records were all already delivered (duplicate/stale); buffered reordering is covered
    // by the sequencer unit tests and the paused-publisher test. Remote records never touch the store.
    @Test
    @Order(4)
    void duplicateAndReorderedBrokerRecordsDoNotMutateAgain() throws Exception {
        Task task = send(cluster.app1, "open dup", null, null).task();
        register(cluster.app2, task, "dup-h");
        try (Watcher onB = new Watcher(cluster.app2, task.id())) {
            assertTrue(send(cluster.app1, "dup-one", get(cluster.app1, task.id()), null).accepted());
            assertTrue(send(cluster.app2, "dup-two", get(cluster.app2, task.id()), null).accepted());
            await().atMost(Duration.ofSeconds(30)).until(() -> receiverGenerations("dup-h").size() >= 2);
            Thread.sleep(3000);
            Task before = get(cluster.app1, task.id());
            List<Long> deliveredBefore = new ArrayList<>(onB.generations);
            int arrivalsBefore = cluster.receiver.arrivalsFor("dup-h").size();
            List<Long> attemptsBefore = orderedAttemptGenerations("dup-h");
            String laneBefore = cluster.laneRow(task.id());
            List<String> real = topicLinesFor(task.id());
            assertFalse(real.isEmpty(), "real committed records are on the topic");

            // Re-produce every real record twice, newest first (duplicate and reordered), through the broker.
            List<String> reordered = new ArrayList<>(real);
            Collections.reverse(reordered);
            List<String> injected = new ArrayList<>(reordered);
            injected.addAll(real);
            String produced = cluster.produceAs("/tmp/new.properties", injected);
            System.out.println("PHASE3-EVIDENCE dup: re-produced " + injected.size() + " records (" + real.size()
                    + " real, reversed then repeated) -> " + produced);
            await().atMost(Duration.ofSeconds(20)).until(() -> topicLinesFor(task.id()).size()
                    >= real.size() + injected.size());
            Thread.sleep(5000);

            Task after = get(cluster.app2, task.id());
            assertEquals(before.generation(), after.generation(), "no further mutation");
            assertEquals(timelineGenerations(before), timelineGenerations(after));
            assertEquals(String.valueOf(before.history()), String.valueOf(after.history()));
            assertEquals(deliveredBefore, onB.generations, "subscriber saw nothing again");
            assertEquals(arrivalsBefore, cluster.receiver.arrivalsFor("dup-h").size(), "no extra webhook arrival");
            assertEquals(attemptsBefore, orderedAttemptGenerations("dup-h"), "no extra HTTP attempt");
            assertEquals(laneBefore, cluster.laneRow(task.id()), "lane cursor unchanged");
            System.out.println("PHASE3-EVIDENCE dup: generation=" + after.generation() + " subscriber="
                    + onB.generations + " attempts=" + attemptsBefore + " lane=" + laneBefore);
        }
    }

    // ---- kill after commit, before publish; restart ----

    @Test
    @Order(5)
    void killAfterCommitBeforePublishKeepsStoredStateAndRestartKeepsGeneration() throws Exception {
        Task task = send(cluster.app1, "open kill", null, null).task();
        try (Watcher onB = new Watcher(cluster.app2, task.id())) {
            cluster.app1.arm(PUBLISH, task.id());
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.submit(() -> send(cluster.app1, "doomed-message", get0(cluster.app1, task.id()), null));
            await().atMost(Duration.ofSeconds(30)).until(() -> cluster.app1.waiting(PUBLISH) >= 1);
            Task committed = get(cluster.app2, task.id());
            long committedGeneration = committed.generation();
            assertTrue(String.valueOf(committed.history()).contains("doomed-message"),
                    "B reads the committed mutation from the shared store while A is held before publishing");
            assertTrue(topicLinesFor(task.id()).stream().noneMatch(line -> line.contains("doomed-message")),
                    "the doomed batch is not on the broker");
            String killedTrace = String.valueOf(cluster.app1.trace(task.id(), "publish-before", "barrier-waiting"));
            String bootBeforeKill = cluster.app1.nodeInfo().get("bootId").getAsString();
            cluster.app1.kill();
            pool.shutdownNow();
            String killedLogs = cluster.app1.logs();
            assertFalse(killedLogs.contains("stopped in"), "SIGKILL: no graceful shutdown ran");
            Thread.sleep(3000);
            assertTrue(onB.generations.stream().noneMatch(g -> g >= committedGeneration),
                    "subscribers on the surviving node never saw the unpublished generation: " + onB.generations);
            System.out.println("PHASE3-EVIDENCE kill: A killed at publish barrier " + killedTrace + " B subscriber="
                    + onB.generations + " broker-has-batch=false");

            // The gap is recoverable by an explicit GetTask on the surviving node (no authorization in the fixture).
            Task recovered = get(cluster.app2, task.id());
            assertEquals(committedGeneration, recovered.generation());
            assertTrue(String.valueOf(recovered.history()).contains("doomed-message"));
            System.out.println("PHASE3-EVIDENCE kill: explicit GetTask on B after the kill: " + describe(recovered));

            cluster.app1.restart(cluster.authorityEnv(1));
            JsonObject info = cluster.app1.nodeInfo();
            System.out.println("PHASE3-EVIDENCE restart: app1 new process " + info + " stored epoch="
                    + cluster.storedEpoch());
            assertNotEquals(bootBeforeKill, info.get("bootId").getAsString(), "the restarted node is a new process");
            Task afterRestart = get(cluster.app1, task.id());
            assertEquals(committedGeneration, afterRestart.generation(), "restart did not reset or change generation");
            Outcome stale = send(cluster.app1, "stale-guard", afterRestart, committedGeneration - 1);
            assertTrue(stale.failure() instanceof TaskGenerationMismatchError, String.valueOf(stale.failure()));
            Outcome next = send(cluster.app1, "after-restart", afterRestart, committedGeneration);
            assertTrue(next.accepted(), String.valueOf(next.failure()));
            long nextGeneration = get(cluster.app2, task.id()).generation();
            assertTrue(nextGeneration > committedGeneration, "generation continues after restart: " + nextGeneration);
            System.out.println("PHASE3-EVIDENCE restart: generation " + committedGeneration + " -> " + nextGeneration
                    + " (stale guard " + committedGeneration + "-1 rejected with TaskGenerationMismatchError)");
        }
    }

    // ---- broker fencing, delayed legacy / obsolete-epoch records ----

    @Test
    @Order(6)
    void deniedOldProducerAndInjectedLegacyAndObsoleteEpochRecordsAreRejected() throws Exception {
        Task task = send(cluster.app1, "open fence", null, null).task();
        try (Watcher onA = new Watcher(cluster.app1, task.id()); Watcher onB = new Watcher(cluster.app2, task.id())) {
            assertTrue(send(cluster.app1, "fence-real", get(cluster.app1, task.id()), null).accepted());
            await().atMost(Duration.ofSeconds(20)).until(() -> !topicLinesFor(task.id()).isEmpty()
                    && onB.generations.size() >= 2);
            Thread.sleep(2000);
            Task before = get(cluster.app1, task.id());
            List<Long> seenA = new ArrayList<>(onA.generations);
            List<Long> seenB = new ArrayList<>(onB.generations);
            List<String> real = topicLinesFor(task.id());
            // The injected records describe the NEXT generation, so a node that wrongly accepted them would deliver
            // a never-committed generation to its subscribers (a copy of a delivered batch would only be stale).
            String lastRecord = real.get(real.size() - 1);
            String nextRecord = Pattern.compile("\"generation\":(\\d+)").matcher(lastRecord)
                    .replaceAll(m -> "\"generation\":" + (Long.parseLong(m.group(1)) + 1));
            assertNotEquals(lastRecord, nextRecord, "the forged record carries generation fields: " + lastRecord);
            JsonObject sample = JsonParser.parseString(nextRecord).getAsJsonObject();

            JsonObject legacy = sample.deepCopy();
            legacy.remove("writerEpoch");
            legacy.addProperty("originId", "oldfleet-origin");
            JsonObject obsolete = sample.deepCopy();
            obsolete.addProperty("writerEpoch", 0);
            obsolete.addProperty("originId", "oldfleet-origin");
            // While the old identity is still allowed by the broker, its records are delivered to the topic.
            String allowed = cluster.produceAs("/tmp/old.properties",
                    List.of(legacy.toString(), obsolete.toString()));
            System.out.println("PHASE3-EVIDENCE fence: old identity produce while still allowed -> " + allowed);
            assertTrue(allowed.contains("[exit=0]"), allowed);
            int onTopic = topicLinesFor(task.id()).size();
            assertEquals(real.size() + 2, onTopic, "the broker accepted the old identity's records");
            String rejectionNeedle = "Rejecting replicated record for task " + task.id();
            await().atMost(Duration.ofSeconds(30)).until(() -> tail(cluster.app1.logs(), rejectionNeedle)
                    .contains("writer epoch 0") && tail(cluster.app2.logs(), rejectionNeedle)
                    .contains("writer epoch 0"));
            Thread.sleep(2000);
            String rejectedA = tail(cluster.app1.logs(), rejectionNeedle);
            String rejectedB = tail(cluster.app2.logs(), rejectionNeedle);
            System.out.println("PHASE3-EVIDENCE fence: node A rejection log: " + rejectedA);
            System.out.println("PHASE3-EVIDENCE fence: node B rejection log: " + rejectedB);
            assertTrue(rejectedA.contains("writer epoch null") && rejectedA.contains("writer epoch 0"), rejectedA);
            assertTrue(rejectedB.contains("writer epoch null") && rejectedB.contains("writer epoch 0"), rejectedB);
            Task after = get(cluster.app2, task.id());
            assertEquals(before.generation(), after.generation(), "injected records did not mutate the store");
            assertEquals(seenA, onA.generations);
            assertEquals(seenB, onB.generations);

            // The operator denies the old identity; its next produce is refused by the broker itself.
            String deny = cluster.kafkaCli("kafka-acls.sh", "--bootstrap-server", "kafka:19092", "--command-config",
                    "/tmp/admin.properties", "--add", "--deny-principal", "User:" + Phase3Cluster.OLD_FLEET_USER,
                    "--operation", "Write", "--topic", Phase3Cluster.TOPIC);
            String acls = cluster.kafkaCli("kafka-acls.sh", "--bootstrap-server", "kafka:19092", "--command-config",
                    "/tmp/admin.properties", "--list", "--topic", Phase3Cluster.TOPIC);
            System.out.println("PHASE3-EVIDENCE fence: deny ACL -> " + deny + " ACLs=" + acls);
            String refused = cluster.produceAs("/tmp/old.properties", List.of(obsolete.toString()));
            System.out.println("PHASE3-EVIDENCE fence: denied old producer record -> " + refused);
            assertTrue(refused.contains("TopicAuthorizationException") || refused.contains("Not authorized"), refused);
            Thread.sleep(2000);
            assertEquals(onTopic, topicLinesFor(task.id()).size(), "the denied record never reached the topic");

            // The current fleet is unaffected.
            assertTrue(send(cluster.app1, "fence-after", get(cluster.app1, task.id()), null).accepted());
            long expected = get(cluster.app1, task.id()).generation();
            await().atMost(Duration.ofSeconds(20)).until(() -> onB.generations.contains(expected));
            Thread.sleep(2000);
            System.out.println("PHASE3-EVIDENCE fence: current fleet still replicates; A subscriber=" + onA.generations
                    + " B subscriber=" + onB.generations);
            assertTrue(onB.strictlyIncreasing(), "B subscriber: " + onB.generations);
        }
    }

    // ---- cross-node webhook order ----

    /** One held-dispatch run: A is held after its lane claim and before HTTP while B commits later work. */
    private record HeldRun(Task task, long heldGeneration, long laterGeneration, String laneWhileHeld) {
    }

    private HeldRun heldOrderRun(String hook, String nodeBText, int filteredBefore) throws Exception {
        Task task = send(cluster.app1, "open " + hook, null, null).task();
        for (int i = 0; i < filteredBefore; i++) {
            assertTrue(send(cluster.app1, "no-config-" + i, get(cluster.app1, task.id()), null).accepted());
        }
        Thread.sleep(2000);
        register(cluster.app2, task, hook);
        long before = get(cluster.app1, task.id()).generation();
        long held = before + 1;
        cluster.app1.arm(WEBHOOK_HTTP, "/hook/" + hook + " gens=" + held + ",");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CompletableFuture<Outcome> sendA = CompletableFuture.supplyAsync(
                () -> send(cluster.app1, "held-on-A " + hook, get0(cluster.app1, task.id()), null), pool);
        await().atMost(Duration.ofSeconds(30)).until(() -> cluster.app1.waiting(WEBHOOK_HTTP) >= 1);
        String laneHeld = cluster.laneRow(task.id());
        List<JsonObject> claim = cluster.app1.trace(task.id() + " " + held + "-" + held, "lane-claim");
        List<JsonObject> start = cluster.app1.trace("/hook/" + hook + " gens=" + held + ",", "http-attempt-start");
        assertEquals(1, claim.size(), "A claimed the shared lane for the held generation");
        assertEquals(1, start.size());
        assertTrue(claim.get(0).get("seq").getAsLong() < start.get(0).get("seq").getAsLong(),
                "claim precedes the HTTP attempt on A");
        assertTrue(laneHeld.contains("claimed_first_generation=" + held) && !laneHeld.contains("holder_token=null"),
                laneHeld);

        Outcome onB = send(cluster.app2, nodeBText, get(cluster.app2, task.id()), null);
        assertTrue(onB.accepted(), String.valueOf(onB.failure()));
        long later = get(cluster.app2, task.id()).generation();
        assertTrue(later > held, "B committed later generations: " + later);
        Thread.sleep(5000);
        assertTrue(cluster.receiver.arrivalsFor(hook).isEmpty(), "receiver has seen nothing while N is paused");
        assertTrue(cluster.app2.trace("/hook/" + hook, "http-attempt-start").isEmpty(),
                "no HTTP attempt for a later generation started on B while N is paused");
        assertTrue(cluster.app1.trace("/hook/" + hook + " gens=" + (held + 1), "http-attempt-start").isEmpty());
        System.out.println("PHASE3-EVIDENCE order[" + hook + "]: held N=" + held + " B committed up to " + later
                + " lane while held=" + laneHeld + " attempts(A,B) before release="
                + attemptStarts(cluster.app1, hook) + "," + attemptStarts(cluster.app2, hook)
                + " receiver arrivals=0");

        cluster.app1.release(WEBHOOK_HTTP);
        sendA.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        return new HeldRun(task, held, later, laneHeld);
    }

    private void assertOrderedAfterRelease(String hook, HeldRun run, boolean expectFinal) throws Exception {
        long last = get(cluster.app1, run.task().id()).generation();
        await().atMost(Duration.ofSeconds(60)).until(() -> receiverGenerations(hook).contains(last));
        Thread.sleep(2000);
        List<Long> attempts = orderedAttemptGenerations(hook);
        List<Long> received = receiverGenerations(hook);
        System.out.println("PHASE3-EVIDENCE order[" + hook + "]: after release attempt-start generations(merged by "
                + "time)=" + attempts + " receiver generations=" + received + " lane="
                + cluster.laneRow(run.task().id()));
        assertTrue(nonDecreasing(attempts), "HTTP attempts never go backwards: " + attempts);
        assertEquals(run.heldGeneration(), attempts.get(0), "the held generation is attempted first");
        assertTrue(attempts.contains(run.laterGeneration()), "later generation attempted after release: " + attempts);
        assertEquals(attempts.stream().distinct().count(), attempts.size(), "one attempt per generation");
        assertTrue(nonDecreasing(received), "receiver sees matching generation order: " + received);
        assertEquals(run.heldGeneration(), received.get(0));
        assertTrue(cluster.laneRow(run.task().id()).contains("next_generation=" + (last + 1)),
                cluster.laneRow(run.task().id()));
        if (expectFinal) {
            List<Phase3Cluster.Receiver.Arrival> arrivals = cluster.receiver.arrivalsFor(hook);
            String lastBody = arrivals.get(arrivals.size() - 1).body();
            assertTrue(lastBody.contains("COMPLETED"), "finalization is the last dispatched event: " + lastBody);
        }
    }

    @Test
    @Order(7)
    void pausedWebhookAttemptOnNodeAKeepsNodeBLaterGenerationsBehindIt() throws Exception {
        HeldRun run = heldOrderRun("ord-a", "later-on-B", 0);
        assertOrderedAfterRelease("ord-a", run, false);

        // Duplicate remote records for the same task do not add attempts.
        List<Long> attempts = orderedAttemptGenerations("ord-a");
        int arrivals = cluster.receiver.arrivalsFor("ord-a").size();
        List<String> real = topicLinesFor(run.task().id());
        cluster.produceAs("/tmp/new.properties", real);
        Thread.sleep(5000);
        assertEquals(attempts, orderedAttemptGenerations("ord-a"), "duplicate remote records dispatch nothing");
        assertEquals(arrivals, cluster.receiver.arrivalsFor("ord-a").size());
        System.out.println("PHASE3-EVIDENCE order[ord-a]: re-produced " + real.size()
                + " duplicate remote records; attempts unchanged " + attempts);
    }

    @Test
    @Order(8)
    void pausedWebhookAttemptWithFinalizationOnNodeB() throws Exception {
        HeldRun run = heldOrderRun("ord-fin", "close it", 0);
        assertOrderedAfterRelease("ord-fin", run, true);
    }

    // Claim limit: generations committed before the webhook was registered are never dispatched and the first claim
    // starts at the registration cursor; no lane trace for the unconfigured generations is asserted.
    @Test
    @Order(9)
    void filteredPositionsAdvanceTheLaneWithoutAWebhook() throws Exception {
        HeldRun run = heldOrderRun("ord-flt", "later-flt", 2);
        assertOrderedAfterRelease("ord-flt", run, false);
        assertTrue(receiverGenerations("ord-flt").stream().allMatch(g -> g >= run.heldGeneration()),
                "generations committed before the webhook existed were filtered, never dispatched: "
                        + receiverGenerations("ord-flt"));
    }

    // ---- crash / quiesce the holder, missing range, deadline loss ----

    @Test
    @Order(10)
    void crashedHolderIsRecoveredWithoutBackwardsDispatch() throws Exception {
        Task task = send(cluster.app1, "open crash-h", null, null).task();
        register(cluster.app2, task, "crash-h");
        long held = get(cluster.app1, task.id()).generation() + 1;
        cluster.app1.arm(WEBHOOK_HTTP, "/hook/crash-h gens=" + held + ",");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> send(cluster.app1, "held-then-killed", get0(cluster.app1, task.id()), null));
        await().atMost(Duration.ofSeconds(30)).until(() -> cluster.app1.waiting(WEBHOOK_HTTP) >= 1);
        String laneClaimed = cluster.laneRow(task.id());
        String holderTrace = String.valueOf(cluster.app1.trace(task.id(), "lane-claim", "http-attempt-start"));
        cluster.app1.kill();
        pool.shutdownNow();
        System.out.println("PHASE3-EVIDENCE crash: holder A killed after claim, before HTTP. lane=" + laneClaimed
                + " A trace=" + holderTrace);
        assertTrue(laneClaimed.contains("claimed_first_generation=" + held));

        List<Long> receiverAtKill = receiverGenerations("crash-h");
        assertTrue(send(cluster.app2, "behind-dead-holder", get(cluster.app2, task.id()), null).accepted());
        long behind = get(cluster.app2, task.id()).generation();
        // B cannot dispatch past the dead holder's claim: it stays behind it (bounded by its own deadlines) and sends
        // nothing until the documented recovery is performed. Observe for 40 s, past the 30-second attempt deadline.
        Thread.sleep(40_000);
        String duringOutage = cluster.laneRow(task.id());
        List<JsonObject> losses = cluster.app2.trace(task.id());
        System.out.println("PHASE3-EVIDENCE crash: B transitions while holder is dead=" + losses + " lane="
                + duringOutage + " receiver=" + receiverGenerations("crash-h"));
        assertFalse(receiverGenerations("crash-h").contains(held), "the dead holder's generation was never sent");
        assertEquals(receiverAtKill, receiverGenerations("crash-h"),
                "B sent nothing behind the dead holder's claim until the explicit recovery");

        String recovery = cluster.app2.admin("POST", "/phase3/recover-lane?task=" + task.id() + "&epoch=1");
        String afterRecovery = cluster.laneRow(task.id());
        System.out.println("PHASE3-EVIDENCE crash: documented lane recovery -> " + recovery + " lane=" + afterRecovery);

        assertTrue(send(cluster.app2, "after-recovery", get(cluster.app2, task.id()), null).accepted());
        long fresh = get(cluster.app2, task.id()).generation();
        await().atMost(Duration.ofSeconds(60)).until(() -> receiverGenerations("crash-h").contains(fresh));
        List<Long> received = receiverGenerations("crash-h");
        System.out.println("PHASE3-EVIDENCE crash: after recovery receiver generations=" + received + " held(retired)="
                + held + " B committed during outage up to " + behind + " fresh=" + fresh + " lane="
                + cluster.laneRow(task.id()));
        assertFalse(received.contains(held), "retired work never dispatches");
        assertTrue(nonDecreasing(received), "no backwards dispatch: " + received);
        assertEquals(received.stream().distinct().count(), received.size());
        cluster.app1.restart(cluster.authorityEnv(1));
        Thread.sleep(5000);
        assertEquals(received, receiverGenerations("crash-h"), "the restarted holder dispatches nothing old");
    }

    @Test
    @Order(11)
    void missingUnclaimedRangeIsRetiredWithoutFabricatedMutations() throws Exception {
        Task task = send(cluster.app1, "open gap-h", null, null).task();
        register(cluster.app2, task, "gap-h");
        long missing = get(cluster.app1, task.id()).generation() + 1;
        cluster.app1.arm(LANE_CLAIM, task.id() + " " + missing + "-");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> send(cluster.app1, "never-claimed", get0(cluster.app1, task.id()), null));
        await().atMost(Duration.ofSeconds(30)).until(() -> cluster.app1.waiting(LANE_CLAIM) >= 1);
        Task committed = get(cluster.app2, task.id());
        assertTrue(String.valueOf(committed.history()).contains("never-claimed"));
        cluster.app1.kill();
        pool.shutdownNow();
        long committedGenerations = committed.generation();

        assertTrue(send(cluster.app2, "after-missing", get(cluster.app2, task.id()), null).accepted());
        long later = get(cluster.app2, task.id()).generation();
        await().atMost(Duration.ofSeconds(90)).until(() -> receiverGenerations("gap-h").contains(later));
        List<JsonObject> bTrace = cluster.app2.trace(task.id());
        System.out.println("PHASE3-EVIDENCE gap: A killed before claiming generation " + missing + " (committed in "
                + "storage up to " + committedGenerations + "); B transitions=" + bTrace + " receiver="
                + receiverGenerations("gap-h") + " lane=" + cluster.laneRow(task.id()));
        assertTrue(countTraces(cluster.app2, task.id(), "lane-retire-gap") >= 1, "B retired the unclaimed range");
        assertFalse(receiverGenerations("gap-h").contains(missing), "the unclaimed generation was not dispatched");
        assertTrue(nonDecreasing(receiverGenerations("gap-h")));
        Task stored = get(cluster.app2, task.id());
        assertEquals(timelineGenerations(stored).size(), timelineGenerations(stored).stream().distinct().count());
        assertTrue(String.valueOf(stored.history()).contains("never-claimed"),
                "the committed mutation stays in storage; only its webhook attempt was lost (no durable delivery)");
        cluster.app1.restart(cluster.authorityEnv(1));
    }

    // ---- obsolete execution output (must run last: it advances the stored epoch) ----

    @Test
    @Order(99)
    void obsoleteExecutionOutputIsRefusedAfterTheStorageEpochAdvances() throws Exception {
        Task task = send(cluster.app1, "open obsolete", null, null).task();
        long before = get(cluster.app1, task.id()).generation();
        cluster.app1.arm(EXECUTOR, "obs");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CompletableFuture<Outcome> inFlight = CompletableFuture.supplyAsync(
                () -> send(cluster.app1, "hold:obs please", get0(cluster.app1, task.id()), null), pool);
        await().atMost(Duration.ofSeconds(30)).until(() -> cluster.app1.waiting(EXECUTOR) >= 1);
        long ingress = get(cluster.app2, task.id()).generation();
        long revisionBefore = cluster.query("SELECT store_revision FROM a2a_tasks WHERE task_id = ?",
                rs -> rs.next() ? rs.getLong(1) : -1L, task.id());
        System.out.println("PHASE3-EVIDENCE obsolete: execution in flight on A at generation " + ingress
                + " (before=" + before + ")");
        String activated = cluster.app2.admin("POST", "/phase3/activate-epoch?epoch=2");
        System.out.println("PHASE3-EVIDENCE obsolete: storage epoch raised " + activated + " stored="
                + cluster.storedEpoch() + " node epochs still 1");
        assertEquals(2L, cluster.storedEpoch());
        cluster.app1.release(EXECUTOR);
        Outcome outcome = inFlight.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        Thread.sleep(3000);
        long generationAfter = cluster.query("SELECT store_revision FROM a2a_tasks WHERE task_id = ?",
                rs -> rs.next() ? rs.getLong(1) : -1L, task.id());
        String refusal = tail(cluster.app1.logs(), task.id());
        System.out.println("PHASE3-EVIDENCE obsolete: in-flight output outcome accepted=" + outcome.accepted()
                + " failure=" + outcome.failure() + " stored revision=" + generationAfter + " (before release " + revisionBefore + ")" + " node A log="
                + refusal);
        assertEquals(revisionBefore, generationAfter, "the obsolete execution output did not mutate the task");
        assertFalse(outcome.accepted(), "the obsolete output was not accepted");
        assertTrue(String.valueOf(outcome.failure()).contains("Task mutation was not committed"),
                String.valueOf(outcome.failure()));
        System.out.println("PHASE3-EVIDENCE obsolete: a storage epoch only fences writers carrying an older epoch; it "
                + "is not a live executor-owner lease and no remote live routing exists");
    }

}
