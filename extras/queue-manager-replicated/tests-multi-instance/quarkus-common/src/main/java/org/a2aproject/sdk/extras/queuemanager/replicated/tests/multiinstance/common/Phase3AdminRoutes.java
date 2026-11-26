package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance.common;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.a2aproject.sdk.extras.taskstore.database.jpa.JpaTaskMigration;
import org.a2aproject.sdk.extras.taskstore.database.jpa.JpaWebhookDispatchGate;
import org.a2aproject.sdk.server.tasks.WebhookDispatchClaim;
import org.a2aproject.sdk.server.tasks.WebhookDispatchLaneState;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Phase 3 fixture-only operator and instrumentation surface of one node: epoch bootstrap at startup (the documented
 * activate + migrateAll step, enabled by {@code a2a.test.bootstrap-epoch}), node identity, barrier control, the
 * ordered trace and the explicit webhook-lane recovery operation. Nothing here is part of the SDK.
 */
@ApplicationScoped
public class Phase3AdminRoutes {

    private static final String BOOT_ID = UUID.randomUUID().toString();

    @Inject
    JpaTaskMigration migration;

    @Inject
    JpaWebhookDispatchGate lanes;

    void onStart(@Observes StartupEvent event) {
        if (!ConfigProvider.getConfig().getOptionalValue("a2a.test.bootstrap-epoch", Boolean.class).orElse(false)) {
            return;
        }
        Long epoch = migration.nodeEpoch();
        if (epoch == null) {
            throw new IllegalStateException("a2a.test.bootstrap-epoch requires a2a.task-store.writer-epoch");
        }
        Long stored = migration.activeEpoch();
        if (stored == null || stored < epoch) {
            migration.activate(epoch);
        }
        int examined = migration.migrateAll();
        Phase3Instrumentation.trace("epoch-bootstrap", "node=" + nodeId() + " epoch=" + epoch + " examined=" + examined);
    }

    void routes(@Observes Router router) {
        router.get("/phase3/node").blockingHandler(ctx -> json(ctx, () -> nodeJson()));
        router.post("/phase3/arm").blockingHandler(ctx -> json(ctx, () -> {
            Phase3Instrumentation.arm(param(ctx, "name"), ctx.request().getParam("match"));
            return "{\"armed\":true}";
        }));
        router.post("/phase3/release").blockingHandler(ctx -> json(ctx, () -> {
            Phase3Instrumentation.release(param(ctx, "name"));
            return "{\"released\":true}";
        }));
        router.get("/phase3/barrier").blockingHandler(ctx -> json(ctx, () -> "{\"waiting\":"
                + Phase3Instrumentation.waiting(param(ctx, "name")) + ",\"arrived\":"
                + Phase3Instrumentation.arrived(param(ctx, "name")) + "}"));
        router.get("/phase3/trace").blockingHandler(ctx -> json(ctx, () -> {
            List<String> lines = Phase3Instrumentation.traceLines();
            return "[" + String.join(",", lines) + "]";
        }));
        router.post("/phase3/activate-epoch").blockingHandler(ctx -> json(ctx, () -> {
            long epoch = Long.parseLong(param(ctx, "epoch"));
            migration.activate(epoch);
            Phase3Instrumentation.trace("epoch-activated", "epoch=" + epoch);
            return "{\"activated\":" + epoch + "}";
        }));
        router.post("/phase3/recover-lane").blockingHandler(ctx -> json(ctx, () -> {
            String taskId = param(ctx, "task");
            long newEpoch = Long.parseLong(param(ctx, "epoch"));
            Optional<WebhookDispatchLaneState> lane = lanes.readLane(taskId);
            if (lane.isEmpty() || lane.get().holderToken() == null || lane.get().claimedFirstGeneration() == null
                    || lane.get().claimedLastGeneration() == null) {
                return "{\"recovered\":false,\"reason\":\"no claimed range\"}";
            }
            WebhookDispatchLaneState state = lane.get();
            WebhookDispatchClaim abandoned = new WebhookDispatchClaim(taskId, state.writerEpoch(),
                    state.holderToken(), state.claimedFirstGeneration(), state.claimedLastGeneration());
            boolean recovered = lanes.recoverQuiescedClaim(abandoned, newEpoch);
            Phase3Instrumentation.trace("lane-recover-requested", taskId + " " + abandoned.firstGeneration() + "-"
                    + abandoned.lastGeneration() + " -> " + recovered);
            return "{\"recovered\":" + recovered + ",\"first\":" + abandoned.firstGeneration() + ",\"last\":"
                    + abandoned.lastGeneration() + "}";
        }));
    }

    private static String nodeId() {
        return ConfigProvider.getConfig().getOptionalValue("a2a.test.node-id", String.class).orElse("unknown");
    }

    private String nodeJson() {
        Long stored = migration.activeEpoch();
        Long node = migration.nodeEpoch();
        return "{\"nodeId\":\"" + nodeId() + "\",\"bootId\":\"" + BOOT_ID + "\",\"pid\":\""
                + ManagementFactory.getRuntimeMXBean().getName() + "\",\"nodeEpoch\":" + node + ",\"storedEpoch\":"
                + stored + "}";
    }

    private static String param(RoutingContext ctx, String name) {
        String value = ctx.request().getParam(name);
        if (value == null) {
            throw new IllegalArgumentException("missing parameter " + name);
        }
        return value;
    }

    private static void json(RoutingContext ctx, Supplier<String> body) {
        try {
            ctx.response().putHeader("Content-Type", "application/json").end(body.get());
        } catch (RuntimeException e) {
            ctx.response().setStatusCode(500).end(String.valueOf(e).replace('"', '\''));
        }
    }
}
