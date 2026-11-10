package org.a2aproject.sdk.server.version;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.a2aproject.sdk.spec.A2AErrorCodes;
import org.junit.jupiter.api.Test;

class ServerVersionPolicyTest {
    @Test
    void legacy10MappingsRemainFrozenWhile11UsesCorrectedMappings() {
        ServerVersionPolicy old = ServerVersionPolicy.forRequest("1.0");
        ServerVersionPolicy modern = ServerVersionPolicy.forRequest("1.1");
        assertEquals(409, old.httpStatus(A2AErrorCodes.TASK_NOT_CANCELABLE.code()));
        assertEquals(400, modern.httpStatus(A2AErrorCodes.TASK_NOT_CANCELABLE.code()));
        assertEquals(415, old.httpStatus(A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED.code()));
        assertEquals(400, modern.httpStatus(A2AErrorCodes.CONTENT_TYPE_NOT_SUPPORTED.code()));
        assertEquals(502, old.httpStatus(A2AErrorCodes.INVALID_AGENT_RESPONSE.code()));
        assertEquals(500, modern.httpStatus(A2AErrorCodes.INVALID_AGENT_RESPONSE.code()));
        for (A2AErrorCodes code : new A2AErrorCodes[] {A2AErrorCodes.UNSUPPORTED_OPERATION,
                A2AErrorCodes.PUSH_NOTIFICATION_NOT_SUPPORTED, A2AErrorCodes.VERSION_NOT_SUPPORTED}) {
            assertEquals("UNIMPLEMENTED", old.grpcStatus(code.code()));
            assertEquals("FAILED_PRECONDITION", modern.grpcStatus(code.code()));
        }
        assertEquals("1.0", org.a2aproject.sdk.spec.AgentInterface.CURRENT_PROTOCOL_VERSION);
    }
}
