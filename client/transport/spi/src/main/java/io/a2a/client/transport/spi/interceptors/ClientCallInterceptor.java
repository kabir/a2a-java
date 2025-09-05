package io.a2a.client.transport.spi.interceptors;

import java.util.List;
import java.util.Map;

import io.a2a.spec.AgentCard;

/**
 * An abstract base class for client-side call interceptors.
 * Interceptors can inspect and modify requests before they are sent,
 * and can also inspect response headers after they are received.
 * This is ideal for concerns like authentication, logging, or tracing.
 */
public abstract class ClientCallInterceptor {

    /**
     * Intercept a client call before the request is sent.
     *
     * @param methodName the name of the protocol method (e.g., 'message/send')
     * @param payload the request payload
     * @param headers the headers to use
     * @param agentCard the agent card (may be {@code null})
     * @param clientCallContext the {@code ClientCallContext} for this call (may be {@code null})
     * @return the potentially modified payload and headers
     */
    public abstract PayloadAndHeaders intercept(String methodName, Object payload, Map<String, String> headers,
                                                AgentCard agentCard, ClientCallContext clientCallContext);

    /**
     * Called when HTTP response headers are received for streaming requests.
     * This method is called once when the streaming connection is established,
     * before any streaming events are received.
     *
     * @param methodName the name of the protocol method (e.g., 'message/send/streaming')
     * @param responseHeaders the HTTP response headers received from the server
     * @param agentCard the agent card (may be {@code null})
     * @param clientCallContext the {@code ClientCallContext} for this call (may be {@code null})
     */
    public void onStreamingResponseHeaders(String methodName, Map<String, List<String>> responseHeaders,
                                          AgentCard agentCard, ClientCallContext clientCallContext) {
        // Default implementation does nothing - subclasses can override
    }
}
