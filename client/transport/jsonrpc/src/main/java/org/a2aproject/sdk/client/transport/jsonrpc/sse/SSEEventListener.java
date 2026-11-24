package org.a2aproject.sdk.client.transport.jsonrpc.sse;

import static org.a2aproject.sdk.util.Assert.checkNotNullParam;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.client.transport.spi.ClientProtocolVersion;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.jsonrpc.JsonRpcCorrelation;
import org.a2aproject.sdk.spec.A2AClientException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.transport.spi.sse.AbstractSSEEventListener;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.grpc.utils.StreamDecodeResult;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.jspecify.annotations.Nullable;

/**
 * JSON-RPC transport implementation of SSE event listener.
 * Handles parsing of JSON-RPC formatted messages from SSE streams.
 */
public class SSEEventListener extends AbstractSSEEventListener {

    private final AtomicBoolean failed = new AtomicBoolean(false);
    private final AtomicReference<Future<Void>> boundFuture = new AtomicReference<>();

    public void bindFuture(Future<Void> future) {
        boundFuture.set(future);
        if (failed.get()) future.cancel(true);
    }
    private static final Logger LOGGER = Logger.getLogger(SSEEventListener.class.getName());
    private final @Nullable String protocolVersion;
    private final @Nullable String expectedRequestId;

    public SSEEventListener(Consumer<StreamingEventKind> eventHandler,
            @Nullable Consumer<Throwable> errorHandler) {
        this(eventHandler, errorHandler, null);
    }

    public SSEEventListener(Consumer<StreamingEventKind> eventHandler, @Nullable Consumer<Throwable> errorHandler, @Nullable String expectedId) {
        super(eventHandler, errorHandler);
        this.protocolVersion = null;
        this.expectedRequestId = expectedId;
    }

    /** Creates the selected-1.1 reader without changing legacy callback decoding. */
    public SSEEventListener(String protocolVersion, String expectedRequestId, TransportObservation observation) {
        super(observation);
        this.protocolVersion = ClientProtocolVersion.validate(protocolVersion);
        if (!"1.1".equals(protocolVersion)) {
            throw new IllegalArgumentException("Raw JSON-RPC SSE observations require protocol version 1.1");
        }
        this.expectedRequestId = checkNotNullParam("expectedRequestId", expectedRequestId);
    }

    @Override
    public void onMessage(ServerSentEvent event, @Nullable Future<Void> completableFuture) {
        if (failed.get()) {
            return;
        }
        if (protocolVersion == null) {
            parseAndHandleMessage(event.data(), completableFuture);
        } else {
            parseAndPublishFrame(event.data(), completableFuture);
        }
    }

    public void onComplete() {
        LOGGER.fine("SSEEventListener.onComplete() called - signaling successful stream completion");
        completeObservation();
    }

    /**
     * Parses a JSON-RPC message and delegates to the base class for event handling.
     *
     * @param message The raw JSON-RPC message string
     * @param future Optional future for controlling the SSE connection
     */
    private void parseAndHandleMessage(String message, @Nullable Future<Void> future) {
        try {
            if (expectedRequestId != null) {
                JsonRpcCorrelation.validate(message, expectedRequestId);
            }
            StreamResponse response = JSONRPCUtils.parseResponseEvent(message);
            StreamingEventKind event = ProtoUtils.FromProto.streamingEventKind(response);
            
            // Delegate to base class for common event handling and auto-close logic
            handleEvent(event, future);
        } catch (A2AClientException error) {
            failed.set(true);
            onError(error, future == null ? boundFuture.get() : future);
        } catch (A2AError error) {
            failed.set(true);
            onError(error, future == null ? boundFuture.get() : future);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseAndPublishFrame(String message, @Nullable Future<Void> future) {
        try {
            String selectedVersion = protocolVersion;
            if (selectedVersion == null) {
                throw new IllegalStateException("Versioned SSE reader has no selected protocol version");
            }
            String requestId = expectedRequestId;
            if (requestId == null) {
                throw new IllegalStateException("Versioned SSE reader has no originating request id");
            }
            StreamDecodeResult decoded = JSONRPCUtils.decodeResponseEvent(message, selectedVersion, requestId);
            long encodedSize = message.getBytes(StandardCharsets.UTF_8).length;
            if (decoded instanceof StreamDecodeResult.Known known) {
                var notes = known.notes();
                handleFrame(new TransportFrame(known.event(), notes.unknownSkipped(),
                        notes.skippedTimelineGenerations(), null, encodedSize), future);
            } else if (decoded instanceof StreamDecodeResult.Ignored ignored) {
                var notes = ignored.notes();
                handleFrame(new TransportFrame(null, true, notes.skippedTimelineGenerations(), null, encodedSize), future);
            } else if (decoded instanceof StreamDecodeResult.LocalRejection rejection) {
                handleFrame(new TransportFrame(null, false, java.util.List.of(), rejection.error(), encodedSize), future);
            } else if (decoded instanceof StreamDecodeResult.Malformed malformed) {
                failObservation(malformed.cause(), future);
            }
        } catch (A2AError error) {
            failObservation(error, future);
        } catch (JsonProcessingException error) {
            failObservation(new RuntimeException("Invalid JSON-RPC SSE response envelope", error), future);
        } catch (RuntimeException error) {
            failObservation(error, future);
        }
    }

}
