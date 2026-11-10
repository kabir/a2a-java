package org.a2aproject.sdk.server.apps.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import com.google.gson.JsonParser;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Runs the real public default client against a 1.1-only server without 0.3 classes. */
public abstract class AbstractOnly11ServerTest {
    @Inject
    @PublicAgentCard
    AgentCard card;

    @Inject
    TestUtilsBean testUtils;

    protected abstract void configureTransport(ClientBuilder builder);

    protected abstract void assertRawVersionRejected(String taskId, @Nullable String version) throws Exception;

    @Test
    public void testOnly11WithoutOptional03Classes() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "org.a2aproject.sdk.compat03.spec.AgentCard_v0_3", false, getClass().getClassLoader()));
        assertEquals(Set.of("1.1"), card.supportedInterfaces().stream()
                .map(iface -> iface.protocolVersion()).collect(Collectors.toSet()));
        Task task = Task.builder().id("only11-packaging-task").contextId("only11-packaging-context")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        testUtils.saveTask(task);
        try {
            ClientBuilder defaultBuilder = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setStreaming(false).build());
            configureTransport(defaultBuilder);
            try (Client client = defaultBuilder.build()) {
                assertEquals(task.id(), client.getTask(new TaskQueryParams(task.id())).id());
            }
            ClientBuilder explicit10 = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().setProtocolVersion("1.0").setStreaming(false).build());
            configureTransport(explicit10);
            assertThrows(A2AClientException.class, explicit10::build);
            for (String version : new String[] {"1.0", "0.3", null, ""}) {
                assertRawVersionRejected(task.id(), version);
            }
        } finally {
            testUtils.deleteTask(task.id());
        }
    }

    protected static void assertJsonVersionError(String body) {
        var error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
        if (error.get("code").getAsInt() == -32009) {
            return;
        }
        var details = error.getAsJsonArray("details");
        assertTrue(details != null && StreamSupport.stream(details.spliterator(), false)
                .map(detail -> detail.getAsJsonObject())
                .anyMatch(detail -> detail.has("reason") && detail.get("reason").getAsString().equals("VERSION_NOT_SUPPORTED")), body);
    }
}
