package org.a2aproject.sdk.server.util.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ProjectingPublisherTest {

    @Test
    void filteredEventsDoNotConsumeDemandAndSynchronousRequestsDoNotRecurse() {
        List<String> sourceItems = new ArrayList<>();
        sourceItems.add("first");
        for (int i = 0; i < 10_000; i++) sourceItems.add("skip-" + i);
        sourceItems.add("second");
        SynchronousPublisher source = new SynchronousPublisher(sourceItems, null);
        List<String> values = new ArrayList<>();
        Flow.Subscription[] subscription = new Flow.Subscription[1];
        boolean[] completed = {false};
        ProjectingPublisher.mapNotNull(source, value -> value.startsWith("skip") ? null : value)
                .subscribe(new Flow.Subscriber<String>() {
                    @Override public void onSubscribe(Flow.Subscription value) { subscription[0] = value; value.request(1); }
                    @Override public void onNext(String value) { values.add(value); }
                    @Override public void onError(Throwable error) { throw new AssertionError(error); }
                    @Override public void onComplete() { completed[0] = true; }
                });

        assertEquals(List.of("first"), values);
        assertFalse(completed[0]);
        subscription[0].request(1);
        assertEquals(List.of("first", "second"), values);
        assertTrue(completed[0]);
        assertEquals(1, source.maxRequestDepth.get());
    }

    @Test
    void terminalErrorPropagatesAfterEveryItemIsFiltered() {
        IllegalStateException failure = new IllegalStateException("upstream failed");
        SynchronousPublisher source = new SynchronousPublisher(List.of("drop", "drop"), failure);
        Throwable[] observed = new Throwable[1];
        ProjectingPublisher.<String, String>mapNotNull(source, value -> null).subscribe(new Flow.Subscriber<String>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(1); }
            @Override public void onNext(String value) { throw new AssertionError("filtered value escaped"); }
            @Override public void onError(Throwable error) { observed[0] = error; }
            @Override public void onComplete() { throw new AssertionError("expected error"); }
        });

        assertSame(failure, observed[0]);
        assertEquals(1, source.maxRequestDepth.get());
    }

    @Test
    void completionPropagatesAfterEveryItemIsFiltered() {
        SynchronousPublisher source = new SynchronousPublisher(List.of("drop", "drop"), null);
        boolean[] completed = {false};
        ProjectingPublisher.<String, String>mapNotNull(source, value -> null).subscribe(new Flow.Subscriber<String>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(1); }
            @Override public void onNext(String value) { throw new AssertionError("filtered value escaped"); }
            @Override public void onError(Throwable error) { throw new AssertionError(error); }
            @Override public void onComplete() { completed[0] = true; }
        });

        assertTrue(completed[0]);
        assertEquals(1, source.maxRequestDepth.get());
    }

    @Test
    void cancellationStopsFilteredItemReplenishment() {
        SynchronousPublisher source = new SynchronousPublisher(List.of("drop", "keep", "unused"), null);
        Flow.Subscription[] subscription = new Flow.Subscription[1];
        ProjectingPublisher.mapNotNull(source, value -> value.equals("keep") ? value : null)
                .subscribe(new Flow.Subscriber<String>() {
                    @Override public void onSubscribe(Flow.Subscription value) { subscription[0] = value; value.request(1); }
                    @Override public void onNext(String value) { subscription[0].cancel(); }
                    @Override public void onError(Throwable error) { throw new AssertionError(error); }
                    @Override public void onComplete() { throw new AssertionError("cancelled stream completed"); }
                });

        assertEquals(2, source.emitted.get());
        assertTrue(source.cancelled.get());
    }

    private static final class SynchronousPublisher implements Flow.Publisher<String> {
        private final List<String> values;
        private final Throwable terminalError;
        private final AtomicInteger maxRequestDepth = new AtomicInteger();
        private final AtomicInteger emitted = new AtomicInteger();
        private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();

        private SynchronousPublisher(List<String> values, Throwable terminalError) {
            this.values = values;
            this.terminalError = terminalError;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int index;
                private boolean done;
                private int requestDepth;

                @Override
                public void request(long n) {
                    requestDepth++;
                    maxRequestDepth.accumulateAndGet(requestDepth, Math::max);
                    try {
                        if (done) return;
                        if (index < values.size()) {
                            emitted.incrementAndGet();
                            subscriber.onNext(values.get(index++));
                        }
                        if (index == values.size()) {
                            done = true;
                            if (terminalError == null) subscriber.onComplete(); else subscriber.onError(terminalError);
                        }
                    } finally {
                        requestDepth--;
                    }
                }

                @Override public void cancel() { done = true; cancelled.set(true); }
            });
        }
    }
}
