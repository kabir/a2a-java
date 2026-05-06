package org.a2aproject.sdk.client.http;

public class AndroidA2AHttpClientSSETest extends AbstractA2AHttpClientSSETest {

    @Override
    protected A2AHttpClient createClient() {
        return new AndroidA2AHttpClient();
    }
}
