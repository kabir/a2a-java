package io.a2a.client.http;

public class JdkA2AHttpClientIntegrationTest extends AbstractA2AHttpClientIntegrationTest {

    @Override
    protected A2AHttpClient createClient() {
        return new JdkA2AHttpClient();
    }
}
