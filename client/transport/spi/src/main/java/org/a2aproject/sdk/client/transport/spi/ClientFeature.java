package org.a2aproject.sdk.client.transport.spi;

/** Capabilities implemented by an SDK transport, independent of Agent Card flags. */
public enum ClientFeature {
    /** Existing send, query, configuration and callback operations. */
    ORDINARY,
    /** Input generation guards are encoded and forwarded without retry or silent fallback. */
    GUARDED_INPUT,
    /** Artifact input is encoded when the agent card enables userArtifacts. */
    ARTIFACT_INPUT,
    /** Unknown protocol variants are skipped with immutable decode evidence retained. */
    UNKNOWN_VARIANT_SKIP
}
