package org.a2aproject.sdk.server.apps.quarkus.rest;

/**
 * Simple POJO representing a JSON-RPC 2.0 error response.
 */
public class JsonRpcErrorResponse {
    private String jsonrpc = "2.0";
    private Object id;
    private ErrorObject error;

    public JsonRpcErrorResponse() {
    }

    public JsonRpcErrorResponse(Object id, int code, String message) {
        this.id = id;
        this.error = new ErrorObject(code, message);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public ErrorObject getError() {
        return error;
    }

    public void setError(ErrorObject error) {
        this.error = error;
    }

    public static class ErrorObject {
        private int code;
        private String message;

        public ErrorObject() {
        }

        public ErrorObject(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
