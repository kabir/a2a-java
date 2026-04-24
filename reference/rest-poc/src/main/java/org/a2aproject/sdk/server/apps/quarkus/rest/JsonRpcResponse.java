package org.a2aproject.sdk.server.apps.quarkus.rest;

/**
 * Simple POJO representing a JSON-RPC 2.0 success response.
 */
public class JsonRpcResponse {
    private String jsonrpc = "2.0";
    private Object id;
    private Object result;

    public JsonRpcResponse() {
    }

    public JsonRpcResponse(Object id, Object result) {
        this.id = id;
        this.result = result;
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

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
