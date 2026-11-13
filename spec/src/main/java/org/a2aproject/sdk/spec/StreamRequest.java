package org.a2aproject.sdk.spec;

import org.a2aproject.sdk.spec.util.Utils;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** Companion request envelope for send-input and task-observation streams. */
public record StreamRequest(@Nullable String tenant, Payload payload) {

    public StreamRequest {
        Utils.validateTenant(tenant);
        Assert.checkNotNullParam("payload", payload);
    }

    public sealed interface Payload permits Send, Subscribe {
    }

    public record Send(SendInputParams sendMessage) implements Payload {
        public Send {
            Assert.checkNotNullParam("sendMessage", sendMessage);
        }
    }

    public record Subscribe(ObserveTaskParams subscribe) implements Payload {
        public Subscribe {
            Assert.checkNotNullParam("subscribe", subscribe);
        }
    }
}
