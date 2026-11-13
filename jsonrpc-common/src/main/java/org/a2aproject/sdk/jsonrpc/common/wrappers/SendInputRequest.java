package org.a2aproject.sdk.jsonrpc.common.wrappers;

import static org.a2aproject.sdk.spec.A2AMethods.SEND_MESSAGE_METHOD;

import org.a2aproject.sdk.spec.SendInputParams;

/** Version-aware JSON-RPC wrapper for the companion send-input request shape. */
public final class SendInputRequest extends NonStreamingJSONRPCRequest<SendInputParams> {

    public SendInputRequest(String jsonrpc, Object id, SendInputParams params) {
        super(jsonrpc, SEND_MESSAGE_METHOD, id, params);
    }
}
