package org.a2aproject.sdk.server.rest.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractOnly11ServerTest;
import org.a2aproject.sdk.server.apps.common.Only11TestProfile;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@QuarkusTest
@TestProfile(Only11TestProfile.class)
public class Only11RestPackagingTest extends AbstractOnly11ServerTest {
    @Inject
    TestUtilsBean taskUtils;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testOneOneSubscribeToTaskWithGetOpensEventStream() throws Exception {
        String taskId = "rest-subscribe-get-test";
        String contextId = "rest-subscribe-get-context";
        taskUtils.saveTask(Task.builder().id(taskId).contextId(contextId)
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING)).build());
        try {
            taskUtils.ensureQueue(taskId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/tasks/" + taskId + ":subscribe"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "text/event-stream")
                    .header("A2A-Version", "1.1")
                    .GET().build();
            HttpResponse<InputStream> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                assertEquals(200, response.statusCode());
                assertTrue(response.headers().firstValue("Content-Type").orElse("")
                        .startsWith("text/event-stream"));
                BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
                String line;
                boolean initialEvent = false;
                // The stream begins with an SSE comment frame; skip it and its blank separator.
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        initialEvent = true;
                        break;
                    }
                }
                assertTrue(initialEvent, "Expected an initial SSE task event");

                taskUtils.enqueueEvent(taskId, TaskStatusUpdateEvent.builder().taskId(taskId).contextId(contextId)
                        .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED)).build());
                boolean completed = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:") && line.contains("TASK_STATE_COMPLETED")) {
                        completed = true;
                        break;
                    }
                }
                assertTrue(completed, "Expected a completed task event");
            }
        } finally {
            taskUtils.deleteTask(taskId);
        }
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class,
                new RestTransportConfigBuilder().httpClient(new JdkA2AHttpClient()));
    }

    @Override
    protected void assertRawVersionRejected(String taskId, @Nullable String version) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/tasks/" + taskId)).GET();
        if (version != null) {
            request.header("A2A-Version", version);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode(), response.body());
        assertJsonVersionError(response.body());
    }
}
