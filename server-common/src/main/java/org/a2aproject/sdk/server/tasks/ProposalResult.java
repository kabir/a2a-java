package org.a2aproject.sdk.server.tasks;

import java.util.List;

import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.util.Assert;
import org.jspecify.annotations.Nullable;

/** Correlated local outcome of processing an executor Task proposal. */
public sealed interface ProposalResult
        permits ProposalResult.Committed, ProposalResult.Rejected, ProposalResult.Failed {

    String correlationId();

    /** The authority committed the proposal, or verified that it changed nothing. */
    record Committed(String correlationId, TaskSnapshot snapshot, List<StreamingEventKind> events,
            boolean changed) implements ProposalResult {
        public Committed {
            Assert.checkNotNullParam("correlationId", correlationId);
            Assert.checkNotNullParam("snapshot", snapshot);
            Assert.checkNotNullParam("events", events);
            events = List.copyOf(events);
        }
    }

    /** A conflict or validation failure that is known to have made no mutation. */
    record Rejected(String correlationId, Kind kind, String reason,
            @Nullable TaskRevision currentRevision) implements ProposalResult {
        public Rejected {
            Assert.checkNotNullParam("correlationId", correlationId);
            Assert.checkNotNullParam("kind", kind);
            Assert.checkNotNullParam("reason", reason);
        }

        public enum Kind {
            CONFLICT,
            VALIDATION
        }
    }

    /** A processing/storage failure, including whether a commit is known not to have happened. */
    record Failed(String correlationId, String reason, Throwable cause,
            CommitCertainty certainty) implements ProposalResult {
        public Failed {
            Assert.checkNotNullParam("correlationId", correlationId);
            Assert.checkNotNullParam("reason", reason);
            Assert.checkNotNullParam("cause", cause);
            Assert.checkNotNullParam("certainty", certainty);
        }

        public enum CommitCertainty {
            KNOWN_NOT_COMMITTED,
            INDETERMINATE
        }
    }
}
