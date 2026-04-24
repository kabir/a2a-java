# Quarkus REST JSON-RPC POC

Proof of concept for using Quarkus REST (reactive) for JSON-RPC endpoints that conditionally return JSON or SSE streams based on request body inspection.

## Purpose

Validate that Quarkus REST can:
- Inspect request body before choosing response type
- Return either JSON or SSE based on method name in request
- Use Gson for serialization even with Jackson on classpath
- Return control to caller immediately while SSE events arrive asynchronously

## Architecture

Single JAX-RS `@POST` endpoint that:
1. Accepts `String` body (bypasses JAX-RS deserialization)
2. Injects Vert.x `RoutingContext` via `@Context`
3. Manually parses body with Gson to extract method name
4. Routes to either:
   - Non-streaming handler → returns JSON immediately
   - Streaming handler → starts SSE stream, returns control, sends events asynchronously via Vert.x timers

## Running the POC

### Start the application

```bash
cd reference/rest-poc
mvn quarkus:dev
```

The server will start on http://localhost:8080

### Test Non-Streaming Method

```bash
curl -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"testNonStreaming","id":"test-123"}'
```

**Expected Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "test-123",
  "result": {
    "message": "Non-streaming response",
    "timestamp": 1234567890
  }
}
```

### Test Streaming Method

```bash
curl -N -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"testStreaming","id":"stream-123"}'
```

**Expected Response (SSE stream):**
```
: SSE stream started

id: 0
data: {"jsonrpc":"2.0","id":"stream-123","result":{"event":1,"timestamp":1234567890}}

id: 1
data: {"jsonrpc":"2.0","id":"stream-123","result":{"event":2,"timestamp":1234567891}}

id: 2
data: {"jsonrpc":"2.0","id":"stream-123","result":{"event":3,"timestamp":1234567892}}
```

### Test Error Handling

**Invalid JSON:**
```bash
curl -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -d '{invalid json}'
```

**Expected:**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32700,
    "message": "Parse error: ..."
  }
}
```

**Unknown Method:**
```bash
curl -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"unknownMethod","id":1}'
```

**Expected:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Method not found: unknownMethod"
  }
}
```

## Running Tests

```bash
mvn test -pl reference/rest-poc
```

## Success Criteria

- [x] Non-streaming method returns single JSON response
- [x] Streaming method returns SSE event stream with multiple events
- [x] Streaming response starts immediately and returns control to caller
- [x] Events arrive asynchronously after endpoint method returns
- [x] Gson is used for all JSON serialization
- [x] Request body inspection determines response type correctly
- [x] Error handling works for invalid JSON and unknown methods

## Next Steps

If successful:
1. Review with Emmanuel
2. Decide whether to migrate `reference/jsonrpc` to Quarkus REST
3. Schedule call with Vert.x Web maintainer
4. Implement full migration if approved
