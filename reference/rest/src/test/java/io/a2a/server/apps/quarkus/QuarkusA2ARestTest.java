package io.a2a.server.apps.quarkus;

import io.a2a.client.ClientBuilder;
import io.a2a.client.transport.rest.RestTransport;
import io.a2a.client.transport.rest.RestTransportConfigBuilder;
import io.a2a.server.apps.common.AbstractA2AServerTest;
import io.a2a.spec.TransportProtocol;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusA2ARestTest extends AbstractA2AServerTest {

    public QuarkusA2ARestTest() {
        super(8081);
//        System.setProperty("jdk.httpclient.HttpClient.log", "errors,requests,headers,frames:all,ssl,trace,channel");
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:8081";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class, new RestTransportConfigBuilder());
    }
}
