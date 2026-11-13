package org.a2aproject.sdk.client.transport.rest.sse;

import java.util.concurrent.Future;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.client.transport.spi.ClientProtocolVersion;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.client.transport.spi.sse.AbstractSSEEventListener;
import org.a2aproject.sdk.client.transport.rest.RestErrorMapper;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.grpc.utils.StreamDecodeResult;
import org.a2aproject.sdk.grpc.utils.StreamDecoder;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.jspecify.annotations.Nullable;

/**
 * REST transport implementation of SSE event listener.
 * Handles parsing of JSON-formatted protobuf messages from REST SSE streams.
 */
public class SSEEventListener extends AbstractSSEEventListener {

    private static final Logger LOGGER = Logger.getLogger(SSEEventListener.class.getName());
    private final @Nullable String protocolVersion;

    public SSEEventListener(Consumer<StreamingEventKind> eventHandler,
            @Nullable Consumer<Throwable> errorHandler) {
        super(eventHandler, errorHandler);
        this.protocolVersion = null;
    }

    /** Creates a selected-1.1 reader that publishes raw decoded transport frames. */
    public SSEEventListener(String protocolVersion, TransportObservation observation) {
        super(observation);
        this.protocolVersion = ClientProtocolVersion.validate(protocolVersion);
        if (!"1.1".equals(protocolVersion)) {
            throw new IllegalArgumentException("Raw REST SSE observations require protocol version 1.1");
        }
    }

    public void onComplete() {
        LOGGER.fine("SSEEventListener.onComplete() called - signaling successful stream completion");
        completeObservation();
    }

    @Override
    public void onMessage(ServerSentEvent event, @Nullable Future<Void> completableFuture) {
        if (protocolVersion != null) {
            parseAndPublishFrame(event.data(), completableFuture);
            return;
        }
        try {
            LOGGER.fine("REST SSE raw data: " + event.data());
            org.a2aproject.sdk.grpc.StreamResponse.Builder builder = org.a2aproject.sdk.grpc.StreamResponse.newBuilder();
            JsonFormat.parser().merge(event.data(), builder);
            parseAndHandleMessage(builder.build(), completableFuture);
        } catch (InvalidProtocolBufferException e) {
            signalTerminal(RestErrorMapper.mapRestError(event.data(), 500));
        }
    }

    private void parseAndPublishFrame(String payload, @Nullable Future<Void> future) {
        String selectedVersion = protocolVersion;
        if (selectedVersion == null) {
            failObservation(new IllegalStateException("Versioned REST SSE reader has no selected protocol version"),
                    future);
            return;
        }
        StreamDecodeResult decoded = StreamDecoder.decodeJson(payload, selectedVersion);
        long encodedSize = payload.getBytes(StandardCharsets.UTF_8).length;
        if (decoded instanceof StreamDecodeResult.Known known) {
            handleFrame(new TransportFrame(known.event(), known.notes().unknownSkipped(),
                    known.notes().skippedTimelineGenerations(), null, encodedSize), future);
        } else if (decoded instanceof StreamDecodeResult.Ignored ignored) {
            handleFrame(new TransportFrame(null, true, ignored.notes().skippedTimelineGenerations(), null, encodedSize),
                    future);
        } else if (decoded instanceof StreamDecodeResult.LocalRejection rejection) {
            handleFrame(new TransportFrame(null, false, java.util.List.of(), rejection.error(), encodedSize), future);
        } else if (decoded instanceof StreamDecodeResult.Malformed malformed) {
            failObservation(malformed.cause(), future);
        }
    }

    /**
     * Parses a StreamResponse protobuf message and delegates to the base class for event handling.
     *
     * @param response The parsed StreamResponse
     * @param future Optional future for controlling the SSE connection
     */
    private void parseAndHandleMessage(StreamResponse response, @Nullable Future<Void> future) {
        StreamingEventKind event;
        switch (response.getPayloadCase()) {
            case MESSAGE ->
                event = ProtoUtils.FromProto.message(response.getMessage());
            case TASK ->
                event = ProtoUtils.FromProto.task(response.getTask());
            case STATUS_UPDATE ->
                event = ProtoUtils.FromProto.taskStatusUpdateEvent(response.getStatusUpdate());
            case ARTIFACT_UPDATE ->
                event = ProtoUtils.FromProto.taskArtifactUpdateEvent(response.getArtifactUpdate());
            default -> {
                LOGGER.warning("Invalid stream response " + response.getPayloadCase());
                signalTerminal(new IllegalStateException("Invalid stream response from server: " + response.getPayloadCase()));
                return;
            }
        }
        
        // Delegate to base class for common event handling and auto-close logic
        handleEvent(event, future);
    }

}
