import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
final class ItkManagedChannelTypeProbe {
    static void configure(ClientBuilder clientBuilder) {
        clientBuilder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
    }
}
