package io.a2a.client.http;

public class AndroidA2AHttpClientIntegrationTest extends AbstractA2AHttpClientIntegrationTest {

    @Override
    protected A2AHttpClient createClient() {
        return new AndroidA2AHttpClient();
    }
}
