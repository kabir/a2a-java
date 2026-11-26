package org.a2aproject.sdk.extras.queuemanager.replicated.tests.multiinstance.common;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.quarkus.arc.Unremovable;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.http.ServerSentEvent;

/**
 * Unqualified {@link A2AHttpClient} used by the SDK's webhook sender in the Phase 3 fixture. It wraps the real JDK
 * client and records, independently of the receiver, the moment each webhook POST attempt starts (after the SDK
 * has claimed the lane) and when it returns. The {@code webhook-http} barrier blocks a POST before it is sent.
 */
@ApplicationScoped
public class Phase3HttpClient {

    @Produces
    @Unremovable
    @ApplicationScoped
    public A2AHttpClient recordingClient() {
        return new Recording(new JdkA2AHttpClient());
    }

    private static final class Recording implements A2AHttpClient {
        private final A2AHttpClient delegate;

        Recording(A2AHttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public GetBuilder createGet() {
            return delegate.createGet();
        }

        @Override
        public A2AHttpClient.PostBuilder createPost() {
            return new RecordingPost(delegate.createPost());
        }

        @Override
        public DeleteBuilder createDelete() {
            return delegate.createDelete();
        }
    }

    private static final class RecordingPost implements A2AHttpClient.PostBuilder {
        private final A2AHttpClient.PostBuilder delegate;
        private String url = "";
        private String body = "";

        RecordingPost(A2AHttpClient.PostBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public A2AHttpClient.PostBuilder url(String s) {
            this.url = s;
            delegate.url(s);
            return this;
        }

        @Override
        public A2AHttpClient.PostBuilder addHeaders(Map<String, String> headers) {
            delegate.addHeaders(headers);
            return this;
        }

        @Override
        public A2AHttpClient.PostBuilder addHeader(String name, String value) {
            delegate.addHeader(name, value);
            return this;
        }

        @Override
        public A2AHttpClient.PostBuilder body(String body) {
            this.body = body;
            delegate.body(body);
            return this;
        }

        @Override
        public A2AHttpClient.PostBuilder followRedirects(boolean follow) {
            delegate.followRedirects(follow);
            return this;
        }

        @Override
        public A2AHttpResponse post() throws IOException, InterruptedException {
            String key = url + " " + summarize(body);
            Phase3Instrumentation.trace("http-attempt-start", key);
            Phase3Instrumentation.pass(Phase3Instrumentation.WEBHOOK_HTTP, key);
            Phase3Instrumentation.trace("http-attempt-sent", key);
            try {
                A2AHttpResponse response = delegate.post();
                Phase3Instrumentation.trace("http-attempt-done", key + " status=" + response.status());
                return response;
            } catch (IOException | InterruptedException | RuntimeException failure) {
                Phase3Instrumentation.trace("http-attempt-failed", key + " " + failure);
                throw failure;
            }
        }

        @Override
        public CompletableFuture<Void> postAsyncSSE(Consumer<ServerSentEvent> messageConsumer,
                Consumer<Throwable> errorConsumer, Runnable completeRunnable) throws IOException, InterruptedException {
            return delegate.postAsyncSSE(messageConsumer, errorConsumer, completeRunnable);
        }
    }

    private static final java.util.regex.Pattern GENERATION =
            java.util.regex.Pattern.compile("\"generation\"\\s*:\\s*\"?(\\d+)\"?");

    /** Lists the generation markers in a webhook body so the trace shows which generations an attempt carries. */
    static String summarize(String body) {
        StringBuilder out = new StringBuilder("gens=");
        java.util.regex.Matcher matcher = GENERATION.matcher(body);
        while (matcher.find()) {
            out.append(matcher.group(1)).append(',');
        }
        return out.toString();
    }
}
