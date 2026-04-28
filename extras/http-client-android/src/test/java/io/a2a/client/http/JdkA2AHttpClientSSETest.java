package io.a2a.client.http;

public class JdkA2AHttpClientSSETest extends AbstractA2AHttpClientSSETest {

    @Override
    protected A2AHttpClient createClient() {
        return new JdkA2AHttpClient();
    }
}
