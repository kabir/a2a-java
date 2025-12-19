package io.a2a.jsonrpc.common.wrappers;

import io.a2a.spec.A2AError;
import io.a2a.spec.EventKind;

/**
 * The response after receiving a send message request.
 */
public final class SendMessageResponse extends A2AResponse<EventKind> {

    /**
     * Constructs response with all parameters.
     *
     * @param jsonrpc the JSON-RPC version
     * @param id the request ID
     * @param result the result
     * @param error the error if any
     */
    public SendMessageResponse(String jsonrpc, Object id, EventKind result, A2AError error) {
        super(jsonrpc, id, result, error, EventKind.class);
    }

    /**
     * Constructs successful response.
     *
     * @param id the request ID
     * @param result the result
     */
    public SendMessageResponse(Object id, EventKind result) {
        this(null, id, result, null);
    }

    /**
     * Constructs error response.
     *
     * @param id the request ID
     * @param error the error
     */
    public SendMessageResponse(Object id, A2AError error) {
        this(null, id, null, error);
    }
}
