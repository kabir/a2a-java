package io.a2a.server;

import io.a2a.spec.A2AError;

public class JSONRPCException extends Exception{
    private final A2AError error;

    public JSONRPCException(A2AError error) {
        this.error = error;
    }

    public A2AError getError() {
        return error;
    }
}
