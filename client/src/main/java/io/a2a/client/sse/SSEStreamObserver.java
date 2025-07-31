package io.a2a.client.sse;


import static io.a2a.grpc.utils.ProtoUtils.FromProto;

import java.util.function.Consumer;
import java.util.logging.Logger;

import io.a2a.grpc.StreamResponse;
import io.a2a.spec.StreamingEventKind;
import io.grpc.stub.StreamObserver;

public class SSEStreamObserver implements StreamObserver<StreamResponse> {

    private static final Logger log = Logger.getLogger(SSEStreamObserver.class.getName());
    private final Consumer<StreamingEventKind> eventHandler;
    private final Consumer<Throwable> errorHandler;

    public SSEStreamObserver(Consumer<StreamingEventKind> eventHandler, Consumer<Throwable> errorHandler) {
        this.eventHandler = eventHandler;
        this.errorHandler = errorHandler;
    }

    @Override
    public void onNext(StreamResponse response) {
        StreamingEventKind event;
        if (response.hasMsg()) {
            event = FromProto.message(response.getMsg());
        } else if (response.hasTask()) {
            event = FromProto.task(response.getTask());
        } else if (response.hasStatusUpdate()) {
            event = FromProto.taskStatusUpdateEvent(response.getStatusUpdate());
        } else if (response.hasArtifactUpdate()) {
            event = FromProto.taskArtifactUpdateEvent(response.getArtifactUpdate());
        } else {
            log.warning("Invalid stream response " + response);
            return;
        }
        eventHandler.accept(event);
    }

    @Override
    public void onError(Throwable t) {
        errorHandler.accept(t);
    }

    @Override
    public void onCompleted() {
        // done
    }
}
