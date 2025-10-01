# A2A Java SDK - Project Overview

## Purpose
Java implementation of the Agent2Agent (A2A) Protocol. This SDK enables building **agent-to-agent communication systems** with both server and client capabilities.

**Core Value Propositions:**
- Host AI agents that can receive and process tasks (Server SDK)
- Communicate with A2A agents (Client SDK)
- Support multiple transport protocols (JSON-RPC 2.0, gRPC, HTTP+JSON/REST)
- Production-ready features (database persistence, distributed replication, push notifications)
- Event-driven architecture with backpressure-aware streaming

## Project Metadata
- **Version**: 0.3.0.Beta2-SNAPSHOT
- **License**: Apache 2.0
- **Repository**: https://github.com/a2aproject/a2a-java-sdk
- **Organization**: a2aproject
- **Protocol Version**: 0.3.0

## Key Architecture Principles
1. **Transport-Agnostic Design**: All three transport protocols funnel through the same `DefaultRequestHandler`
2. **Event Queue Architecture**: Complex producer-consumer pattern with parent-child queue relationships ("tapping")
3. **CDI-Based Extensibility**: Heavy use of Jakarta CDI for pluggable components
4. **Reactive Streaming**: Uses Java Flow API + Mutiny Zero for backpressure-aware streaming

## Main Use Cases
1. **Build A2A Server Agents**: Implement AgentCard + AgentExecutor to expose agent capabilities
2. **Build A2A Clients**: Connect to and communicate with A2A server agents
3. **Multi-Protocol Support**: Same agent logic works across JSON-RPC, gRPC, and REST
4. **Production Deployment**: Database persistence, distributed queue replication, push notifications
