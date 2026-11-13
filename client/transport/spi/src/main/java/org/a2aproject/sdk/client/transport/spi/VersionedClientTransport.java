package org.a2aproject.sdk.client.transport.spi;

import java.util.List;
import java.util.Set;

import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.jspecify.annotations.Nullable;

/** Additive companion for transports with an immutable, exact protocol selection. */
public interface VersionedClientTransport extends ClientTransport {
    String protocolVersion();

    /** Implemented capabilities for this instance's selected version. */
    Set<ClientFeature> capabilities();

    /** Starts an asynchronous input request with the full decoded response envelope. */
    default TransportCall<TransportResult<EventKind>> sendInput(SendInputParams params, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        throw unsupported("sendInput");
    }

    /** Starts a streaming input observation. */
    default TransportObservation streamInput(SendInputParams params, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        throw unsupported("streamInput");
    }

    /** Starts an observation from a task cursor. */
    default TransportObservation observe(ObserveTaskParams params, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        throw unsupported("observe");
    }

    /** Starts an asynchronous get with its decoded timeline evidence. */
    default TransportCall<TransportResult<Task>> getTaskAsync(TaskQueryParams params, SpiCallOptions options,
            @Nullable ClientCallContext context) {
        throw unsupported("getTaskAsync");
    }

    /** Performs an ordinary get while retaining per-task decode evidence. */
    default TransportResult<Task> getTaskDecoded(TaskQueryParams params, @Nullable ClientCallContext context) {
        throw unsupported("getTaskDecoded");
    }

    /** Performs an ordinary list while retaining per-task decode evidence. */
    default TransportResult<ListTasksResult> listTasksDecoded(ListTasksParams params,
            @Nullable ClientCallContext context) {
        throw unsupported("listTasksDecoded");
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("Transport does not support " + operation);
    }
}
