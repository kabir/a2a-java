package org.a2aproject.sdk.jsonrpc.common.wrappers;

import static org.a2aproject.sdk.spec.A2AMethods.SUBSCRIBE_TO_TASK_METHOD;

import org.a2aproject.sdk.spec.ObserveTaskParams;

/** Version-aware JSON-RPC wrapper for task observation with an optional generation cursor. */
public final class ObserveTaskRequest extends StreamingJSONRPCRequest<ObserveTaskParams> {

    public ObserveTaskRequest(String jsonrpc, Object id, ObserveTaskParams params) {
        super(jsonrpc, SUBSCRIBE_TO_TASK_METHOD, id, params);
    }
}
