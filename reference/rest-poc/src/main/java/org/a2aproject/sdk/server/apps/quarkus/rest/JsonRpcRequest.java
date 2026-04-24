package org.a2aproject.sdk.server.apps.quarkus.rest;

/**
 * Simple POJO representing a JSON-RPC 2.0 request.
 */
public class JsonRpcRequest {
    private String jsonrpc;
    private Object id;
    private String method;
    private Object params;

    public JsonRpcRequest() {
    }

    public JsonRpcRequest(String jsonrpc, Object id, String method, Object params) {
        this.jsonrpc = jsonrpc;
        this.id = id;
        this.method = method;
        this.params = params;
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

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }
}
