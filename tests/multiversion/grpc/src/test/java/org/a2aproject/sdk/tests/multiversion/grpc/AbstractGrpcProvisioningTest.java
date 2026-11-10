package org.a2aproject.sdk.tests.multiversion.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import com.google.rpc.ErrorInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.MetadataUtils;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.apps.common.TestUtilsBean;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Exercises actual old and shared gRPC services for one provisioned default-endpoint subset. */
public abstract class AbstractGrpcProvisioningTest {
    @Inject
    TestUtilsBean testUtils;

    @Inject
    ProtocolVersionCapture protocolVersionCapture;

    @Inject
    @PublicAgentCard
    AgentCard card;

    @Inject
    @ConfigProperty(name = "test.a2a.versions")
    Set<String> versions;

    @Test
    public void testProvisionedServices() throws Exception {
        Task task = Task.builder().id("grpc-provisioning-task").contextId("grpc-provisioning-context")
                .status(new TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build();
        testUtils.saveTask(task);
        ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:8081").usePlaintext().build();
        try {
            assertEquals(versions, card.supportedInterfaces().stream()
                    .map(iface -> iface.protocolVersion()).collect(java.util.stream.Collectors.toSet()));
            if (versions.contains("0.3")) {
                Public03ClientSupport.assertGet(card.supportedInterfaces().get(0).url(), task.id());
                Public03ClientSupport.assertSend(card.supportedInterfaces().get(0).url());
            }
            for (String version : List.of("1.0", "1.1")) {
                if (versions.contains(version)) {
                    assertPublicSharedClient(task.id(), version);
                }
            }
            for (String version : List.of("0.3", "1.0", "1.1", "2.0")) {
                assertSharedService(channel, task.id(), version,
                        !version.equals("0.3") && versions.contains(version));
                assertHistoricalService(channel, task.id(), version,
                        version.equals("0.3") && versions.contains("0.3"));
            }
            for (String missingVersion : new String[] {null, ""}) {
                assertSharedService(channel, task.id(), missingVersion, false);
                assertHistoricalService(channel, task.id(), missingVersion, versions.contains("0.3"));
            }
            assertWhitespaceMetadataRejectedBeforeApplication(task.id());
        } finally {
            testUtils.deleteTask(task.id());
            channel.shutdownNow();
            channel.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void assertPublicSharedClient(String taskId, String version) throws Exception {
        ClientBuilder builder = Client.builder(card).clientConfig(new ClientConfig.Builder()
                .setProtocolVersion(version).setStreaming(false).build());
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
        try (Client client = builder.build()) {
            assertEquals(taskId, client.getTask(new TaskQueryParams(taskId)).id());
        }
    }

    private void assertSharedService(ManagedChannel channel, String taskId, @Nullable String version,
            boolean accepted) throws Exception {
        var stub = A2AServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(version)));
        GetTaskRequest request = GetTaskRequest.newBuilder().setId(taskId).build();
        if (accepted) {
            assertEquals(taskId, stub.getTask(request).getId());
        } else {
            StatusRuntimeException error = assertThrows(StatusRuntimeException.class, () -> stub.getTask(request));
            com.google.rpc.Status status = StatusProto.fromThrowable(error);
            assertNotNull(status, error.getMessage());
            boolean versionError = false;
            for (var detail : status.getDetailsList()) {
                if (detail.is(ErrorInfo.class) && detail.unpack(ErrorInfo.class).getReason().equals("VERSION_NOT_SUPPORTED")) {
                    versionError = true;
                }
            }
            assertTrue(versionError, error.getMessage());
        }
    }

    private void assertHistoricalService(ManagedChannel channel, String taskId, @Nullable String version,
            boolean accepted) {
        var stub = org.a2aproject.sdk.compat03.grpc.A2AServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(version)));
        var request = org.a2aproject.sdk.compat03.grpc.GetTaskRequest.newBuilder().setName("tasks/" + taskId).build();
        if (accepted) {
            assertEquals(taskId, stub.getTask(request).getId());
        } else {
            StatusRuntimeException error = assertThrows(StatusRuntimeException.class, () -> stub.getTask(request));
            assertEquals(Status.Code.UNIMPLEMENTED, error.getStatus().getCode());
            assertTrue(error.getMessage().contains("VERSION_NOT_SUPPORTED"), error.getMessage());
        }
    }

    private void assertWhitespaceMetadataRejectedBeforeApplication(String taskId) throws InterruptedException {
        protocolVersionCapture.drainProtocolVersions();
        // A malformed HTTP/2 field can tear down its connection. Each service needs its
        // own connection so the next probe does not merely observe the preceding GOAWAY.
        for (boolean historical : List.of(false, true)) {
            ManagedChannel malformed = ManagedChannelBuilder.forTarget("localhost:8081").usePlaintext().build();
            try {
                StatusRuntimeException error;
                if (historical) {
                    var stub = org.a2aproject.sdk.compat03.grpc.A2AServiceGrpc.newBlockingStub(malformed)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(" ")));
                    var request = org.a2aproject.sdk.compat03.grpc.GetTaskRequest.newBuilder().setName("tasks/" + taskId).build();
                    error = assertThrows(StatusRuntimeException.class, () -> stub.getTask(request));
                } else {
                    var stub = A2AServiceGrpc.newBlockingStub(malformed)
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(" ")));
                    error = assertThrows(StatusRuntimeException.class,
                            () -> stub.getTask(GetTaskRequest.newBuilder().setId(taskId).build()));
                }
                assertEquals(Status.Code.INTERNAL, error.getStatus().getCode(), error.toString());
                assertTrue(error.getMessage().contains("PROTOCOL_ERROR"), error.getMessage());
            } finally {
                malformed.shutdownNow();
                assertTrue(malformed.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
        assertTrue(protocolVersionCapture.drainProtocolVersions().isEmpty(),
                "Whitespace-only HTTP/2 field values must be rejected before application interceptors");
    }

    private Metadata metadata(@Nullable String version) {
        Metadata metadata = new Metadata();
        if (version != null) {
            metadata.put(Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER), version);
        }
        return metadata;
    }
}
