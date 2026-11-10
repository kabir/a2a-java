package org.a2aproject.sdk.server.version;

import org.a2aproject.sdk.spec.A2AErrorCodes;
import org.jspecify.annotations.Nullable;

/** Immutable request policy; 1.1 registrations are experimental until the full contract is implemented. */
public enum ServerVersionPolicy {
    V0_3("0.3"), V1_0("1.0"), V1_1("1.1");

    private final String protocolVersion;

    ServerVersionPolicy(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String protocolVersion() {
        return protocolVersion;
    }

    /** Resolves the binding default without inferring a version from the SDK or service name. */
    public static String resolveVersion(@Nullable String requested) {
        return requested == null || requested.isBlank() ? "0.3" : requested.trim();
    }

    /** Unknown requests retain the legacy error envelope while version validation rejects them. */
    public static ServerVersionPolicy forRequest(@Nullable String requested) {
        return switch (resolveVersion(requested)) {
            case "0.3" -> V0_3;
            case "1.1" -> V1_1;
            default -> V1_0;
        };
    }

    public int httpStatus(int code) {
        A2AErrorCodes error = A2AErrorCodes.fromCode(code);
        if (error == null) {
            return 500;
        }
        if (this == V1_1) {
            return switch (error) {
                case TASK_NOT_CANCELABLE, CONTENT_TYPE_NOT_SUPPORTED -> 400;
                case INVALID_AGENT_RESPONSE -> 500;
                default -> error.httpCode();
            };
        }
        return error.httpCode();
    }

    public String grpcStatus(int code) {
        A2AErrorCodes error = A2AErrorCodes.fromCode(code);
        if (error == null) {
            return "UNKNOWN";
        }
        if (this == V1_1) {
            return switch (error) {
                case PUSH_NOTIFICATION_NOT_SUPPORTED, UNSUPPORTED_OPERATION, VERSION_NOT_SUPPORTED -> "FAILED_PRECONDITION";
                default -> error.grpcStatus();
            };
        }
        return error.grpcStatus();
    }
}
