/**
 * Proof of concept for using Quarkus REST (reactive) for JSON-RPC endpoints.
 *
 * <p>This POC demonstrates:
 * <ul>
 *   <li>Inspecting request body to determine response type (JSON vs SSE)</li>
 *   <li>Manual response handling via Vert.x HttpServerResponse</li>
 *   <li>Async SSE streaming with Vert.x timers</li>
 *   <li>Gson serialization with Jackson on classpath</li>
 * </ul>
 */
package org.a2aproject.sdk.server.apps.quarkus.rest;
