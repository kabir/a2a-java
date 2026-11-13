package org.a2aproject.sdk.client.transport.spi;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** A cancellable unary transport operation with one terminal result. */
public final class TransportCall<T> {
    private final CompletableFuture<T> result = new CompletableFuture<>();
    private final Runnable cancelAction;
    private final AtomicBoolean terminal = new AtomicBoolean();

    private TransportCall(Runnable cancelAction) {
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
    }

    /** Creates a pending call whose cancellation detaches its underlying read. */
    public static <T> TransportCall<T> create(Runnable cancelAction) {
        return new TransportCall<>(cancelAction);
    }

    /** Returns the result stage. */
    public CompletionStage<T> result() {
        return result.minimalCompletionStage();
    }

    /** Completes this call once with a value. */
    public boolean complete(T value) {
        Objects.requireNonNull(value, "value");
        return terminal.compareAndSet(false, true) && result.complete(value);
    }

    /** Completes this call once with a failure. */
    public boolean fail(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        return terminal.compareAndSet(false, true) && result.completeExceptionally(failure);
    }

    /** Cancels the local read and completes the result exceptionally once. */
    public void cancel() {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        try {
            cancelAction.run();
        } finally {
            result.completeExceptionally(new CancellationException("Transport call cancelled"));
        }
    }
}
