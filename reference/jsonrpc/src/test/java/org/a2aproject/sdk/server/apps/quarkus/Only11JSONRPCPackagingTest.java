package org.a2aproject.sdk.server.apps.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.server.apps.common.AbstractOnly11ServerTest;
import org.a2aproject.sdk.server.apps.common.Only11TestProfile;
import org.jspecify.annotations.Nullable;

@QuarkusTest
@TestProfile(Only11TestProfile.class)
public class Only11JSONRPCPackagingTest extends AbstractOnly11ServerTest {
    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(JSONRPCTransport.class,
                new JSONRPCTransportConfigBuilder().httpClient(new JdkA2AHttpClient()));
    }

    @Override
    protected void assertRawVersionRejected(String taskId, @Nullable String version) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":\"only11\",\"method\":\"GetTask\",\"params\":{\"id\":\"" + taskId + "\"}}"));
        if (version != null) {
            request.header("A2A-Version", version);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertJsonVersionError(response.body());
    }
}
