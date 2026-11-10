package org.a2aproject.sdk.server.grpc.quarkus;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import com.google.rpc.ErrorInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
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
import org.a2aproject.sdk.server.apps.common.AbstractOnly11ServerTest;
import org.a2aproject.sdk.server.apps.common.Only11TestProfile;
import org.jspecify.annotations.Nullable;

@QuarkusTest
@TestProfile(Only11TestProfile.class)
public class Only11GrpcPackagingTest extends AbstractOnly11ServerTest {

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
    }

    @Override
    protected void assertRawVersionRejected(String taskId, @Nullable String version) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:8081").usePlaintext().build();
        try {
            Metadata metadata = new Metadata();
            if (version != null) {
                metadata.put(Metadata.Key.of("a2a-version", Metadata.ASCII_STRING_MARSHALLER), version);
            }
            var stub = A2AServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                    () -> stub.getTask(GetTaskRequest.newBuilder().setId(taskId).build()));
            com.google.rpc.Status status = StatusProto.fromThrowable(error);
            assertNotNull(status, error.getMessage());
            boolean versionError = false;
            for (var detail : status.getDetailsList()) {
                if (detail.is(ErrorInfo.class) && detail.unpack(ErrorInfo.class).getReason().equals("VERSION_NOT_SUPPORTED")) {
                    versionError = true;
                }
            }
            assertTrue(versionError, error.getMessage());
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

}
