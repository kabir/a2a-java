package org.a2aproject.sdk.grpc.utils;

import org.a2aproject.sdk.spec.StreamError;
import org.a2aproject.sdk.spec.StreamingEventKind;

/** Distinguishes known events, safely skipped future variants, local rejection, and malformed wire data. */
public sealed interface StreamDecodeResult permits StreamDecodeResult.Known, StreamDecodeResult.Ignored,
        StreamDecodeResult.LocalRejection, StreamDecodeResult.Malformed {

    record Known(StreamingEventKind event, DecodeNotes notes) implements StreamDecodeResult {
    }

    record Ignored(DecodeNotes notes) implements StreamDecodeResult {
    }

    record LocalRejection(StreamError error) implements StreamDecodeResult {
    }

    record Malformed(Throwable cause) implements StreamDecodeResult {
    }
}
