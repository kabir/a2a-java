package org.a2aproject.sdk.client.transport.grpc;

import static org.a2aproject.sdk.grpc.utils.ProtoUtils.FromProto;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import org.a2aproject.sdk.client.transport.spi.TransportFrame;
import org.a2aproject.sdk.client.transport.spi.TransportObservation;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.DecodeNotes;
import org.a2aproject.sdk.grpc.utils.StreamDecodeResult;
import org.a2aproject.sdk.grpc.utils.StreamDecoder;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.jspecify.annotations.Nullable;

/** Handles legacy callbacks and the version-aware, cancellable Phase 2 gRPC read path. */
public class EventStreamObserver implements ClientResponseObserver<Object, StreamResponse> {

    private final @Nullable Consumer<StreamingEventKind> eventHandler;
    private final @Nullable Consumer<Throwable> errorHandler;
    private final String protocolVersion;
    private final @Nullable TransportObservation observation;
    private final AtomicReference<ClientCallStreamObserver<Object>> responseStream = new AtomicReference<>();
    private final AtomicBoolean cancellationRequested;
    private final AtomicBoolean terminal = new AtomicBoolean();

    /** Retains the historic 1.0 observer contract. */
    public EventStreamObserver(Consumer<StreamingEventKind> eventHandler,
            @Nullable Consumer<Throwable> errorHandler) {
        this.eventHandler = eventHandler;
        this.errorHandler = errorHandler;
        this.protocolVersion = "1.0";
        this.observation = null;
        this.cancellationRequested = new AtomicBoolean();
    }

    /** Creates a version-aware observer that publishes bounded raw frames. */
    public EventStreamObserver(String protocolVersion, TransportObservation observation) {
        this(protocolVersion, observation, new AtomicBoolean());
    }

    /** Shares cancellation intent with the transport while the observer is being installed. */
    EventStreamObserver(String protocolVersion, TransportObservation observation,
            AtomicBoolean cancellationRequested) {
        this.eventHandler = null;
        this.errorHandler = null;
        this.protocolVersion = protocolVersion;
        this.observation = observation;
        this.cancellationRequested = cancellationRequested;
    }

    @Override
    public void beforeStart(ClientCallStreamObserver<Object> requestStream) {
        if (observation == null) {
            return;
        }
        responseStream.set(requestStream);
        requestStream.disableAutoRequestWithInitial(1);
        if (cancellationRequested.get()) {
            requestStream.cancel("A2A stream cancelled", null);
        }
    }

    /** Detaches the underlying gRPC read, including cancellation requested before call startup. */
    public void cancel() {
        cancellationRequested.set(true);
        ClientCallStreamObserver<Object> current = responseStream.get();
        if (current != null) {
            current.cancel("A2A stream cancelled", null);
        }
    }

    @Override
    public void onNext(StreamResponse response) {
        if (observation == null) {
            legacyOnNext(response);
            return;
        }
        if (terminal.get()) {
            return;
        }
        observation.markReady();
        StreamDecodeResult decoded = StreamDecoder.decode(response, protocolVersion);
        boolean accepted;
        if (decoded instanceof StreamDecodeResult.Known known) {
            DecodeNotes notes = known.notes();
            accepted = observation.publish(new TransportFrame(known.event(), notes.unknownSkipped(),
                    notes.skippedTimelineGenerations(), null, response.getSerializedSize()));
        } else if (decoded instanceof StreamDecodeResult.Ignored ignored) {
            DecodeNotes notes = ignored.notes();
            accepted = observation.publish(new TransportFrame(null, true, notes.skippedTimelineGenerations(),
                    null, response.getSerializedSize()));
        } else if (decoded instanceof StreamDecodeResult.LocalRejection rejection) {
            accepted = observation.publish(new TransportFrame(null, false, java.util.List.of(), rejection.error(),
                    response.getSerializedSize()));
        } else {
            Throwable cause = ((StreamDecodeResult.Malformed) decoded).cause();
            if (terminal.compareAndSet(false, true)) {
                observation.fail(new A2AClientException("Malformed gRPC stream response: " + cause, cause));
                cancel();
            }
            return;
        }
        if (accepted && !terminal.get()) {
            ClientCallStreamObserver<Object> current = responseStream.get();
            if (current != null) {
                current.request(1);
            }
        }
    }

    private void legacyOnNext(StreamResponse response) {
        StreamingEventKind event;
        switch (response.getPayloadCase()) {
            case MESSAGE -> event = FromProto.message(response.getMessage());
            case TASK -> event = FromProto.task(response.getTask());
            case STATUS_UPDATE -> event = FromProto.taskStatusUpdateEvent(response.getStatusUpdate());
            case ARTIFACT_UPDATE -> event = FromProto.taskArtifactUpdateEvent(response.getArtifactUpdate());
            default -> {
                if (errorHandler != null) {
                    errorHandler.accept(new IllegalStateException(
                            "Invalid stream response from server: " + response.getPayloadCase()));
                }
                return;
            }
        }
        if (eventHandler != null) {
            eventHandler.accept(event);
        }
    }

    @Override
    public void onError(Throwable failure) {
        if (observation != null) {
            if (terminal.compareAndSet(false, true)) {
                observation.fail(GrpcErrorMapper.mapGrpcError(failure, "gRPC stream failed: "));
            }
            return;
        }
        if (errorHandler != null) {
            if (failure instanceof io.grpc.StatusRuntimeException || failure instanceof io.grpc.StatusException) {
                errorHandler.accept(GrpcErrorMapper.mapGrpcError(failure));
            } else {
                errorHandler.accept(failure);
            }
        }
    }

    @Override
    public void onCompleted() {
        if (observation != null) {
            if (terminal.compareAndSet(false, true)) {
                observation.markReady();
                observation.complete();
            }
            return;
        }
        if (errorHandler != null) {
            errorHandler.accept(null);
        }
    }
}
