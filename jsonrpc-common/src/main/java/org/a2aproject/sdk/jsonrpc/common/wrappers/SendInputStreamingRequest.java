package org.a2aproject.sdk.jsonrpc.common.wrappers;

import static org.a2aproject.sdk.spec.A2AMethods.SEND_STREAMING_MESSAGE_METHOD;

import org.a2aproject.sdk.spec.SendInputParams;

/** Version-aware JSON-RPC wrapper for streaming companion send input. */
public final class SendInputStreamingRequest extends StreamingJSONRPCRequest<SendInputParams> {

    public SendInputStreamingRequest(String jsonrpc, Object id, SendInputParams params) {
        super(jsonrpc, SEND_STREAMING_MESSAGE_METHOD, id, params);
    }
}
