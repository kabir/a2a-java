package io.a2a.server.grpc.quarkus;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.a2a.server.PublicAgentCard;
import io.a2a.grpc.handler.GrpcHandler;
import io.a2a.server.requesthandlers.CallContextFactory;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.AgentCard;
import io.quarkus.grpc.GrpcService;

@GrpcService
public class QuarkusGrpcHandler extends GrpcHandler {

    @Inject
    Instance<CallContextFactory> callContextFactory;

    @Inject
    public QuarkusGrpcHandler(@PublicAgentCard AgentCard agentCard, RequestHandler requestHandler) {
        super(agentCard, requestHandler);
    }

    @Override
    protected CallContextFactory getCallContextFactory() {
        if (callContextFactory != null && !callContextFactory.isUnsatisfied()) {
            return callContextFactory.get();
        }
        return null;
    }
}