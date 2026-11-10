package org.a2aproject.sdk.tests.multiversion.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.server.apps.common.AbstractPairedVersionServerTest;
import org.a2aproject.sdk.server.apps.common.PairedVersionTestProfile;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PairedVersionTestProfile.class)
public class MultiVersionGrpcTest extends AbstractPairedVersionServerTest {

    @Inject
    ProtocolVersionCapture protocolVersionCapture;

    @Override
    protected List<String> drainProtocolVersions() {
        return protocolVersionCapture.drainProtocolVersions();
    }

    public MultiVersionGrpcTest() {
        super(8081);
    }

    @Test
    public void testPublic03GetAgainstRegisteredSameServer() throws Exception {
        saveTaskInTaskStore(MINIMAL_TASK);
        try {
            Public03ClientSupport.assertGet(getTransportUrl(), MINIMAL_TASK.id());
        } finally {
            deleteTaskInTaskStore(MINIMAL_TASK.id());
        }
    }

    @Test
    public void testPublic03SendAgainstRegisteredSameServer() throws Exception {
        Public03ClientSupport.assertSend(getTransportUrl());
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "localhost:8081";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
    }

    @Override
    protected void assertRawProvisionedVersion(String tenant, @Nullable String version, boolean accepted) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(getTransportUrl()).usePlaintext().build();
        try {
            Metadata headers = new Metadata();
            if (version != null) {
                headers.put(Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER), version);
            }
            var stub = A2AServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
            GetTaskRequest request = GetTaskRequest.newBuilder().setId(MINIMAL_TASK.id()).setTenant(tenant).build();
            if (version != null && !version.isEmpty() && version.isBlank()) {
                drainProtocolVersions();
                StatusRuntimeException error = assertThrows(StatusRuntimeException.class, () -> stub.getTask(request));
                assertEquals(Status.Code.INTERNAL, error.getStatus().getCode());
                assertTrue(error.getMessage().contains("PROTOCOL_ERROR"), error.getMessage());
                assertTrue(drainProtocolVersions().isEmpty(),
                        "Whitespace-only HTTP/2 field values must not reach application interceptors");
                return;
            }
            // The shared 1.x service cannot decode the distinct historical 0.3 wire service.
            boolean sharedAccepted = accepted && version != null && !version.isBlank() && !version.equals("0.3");
            if (sharedAccepted) {
                assertEquals(MINIMAL_TASK.id(), stub.getTask(request).getId());
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
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Override
    public void testAgentCardHeaders() {
        // Skip - gRPC doesn't use HTTP caching headers for Agent Card
    }
}
