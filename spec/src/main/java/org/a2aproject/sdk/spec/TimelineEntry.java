package org.a2aproject.sdk.spec;

import org.a2aproject.sdk.util.Assert;

/** A message or status observation stamped with the task generation at which it was recorded. */
public record TimelineEntry(long generation, Payload entry) {

    public TimelineEntry {
        Assert.checkNotNullParam("entry", entry);
    }

    public sealed interface Payload permits MessageValue, StatusValue {
    }

    public record MessageValue(Message message) implements Payload {
        public MessageValue {
            Assert.checkNotNullParam("message", message);
        }
    }

    public record StatusValue(TaskStatus status) implements Payload {
        public StatusValue {
            Assert.checkNotNullParam("status", status);
        }
    }
}
