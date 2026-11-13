package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.ObserveTaskParams;
import org.mapstruct.Mapper;

/** Maps a task observation request and retains optional cursor presence. */
@Mapper(config = A2AProtoMapperConfig.class)
public interface ObserveTaskParamsMapper {

    ObserveTaskParamsMapper INSTANCE = A2AMappers.getMapper(ObserveTaskParamsMapper.class);

    default org.a2aproject.sdk.grpc.SubscribeToTaskRequest toProto(ObserveTaskParams domain) {
        org.a2aproject.sdk.grpc.SubscribeToTaskRequest.Builder builder =
                org.a2aproject.sdk.grpc.SubscribeToTaskRequest.newBuilder().setId(domain.id());
        if (domain.tenant() != null) {
            builder.setTenant(domain.tenant());
        }
        if (domain.lastSeenGeneration() != null) {
            builder.setLastSeenGeneration(domain.lastSeenGeneration());
        }
        return builder.build();
    }

    default ObserveTaskParams fromProto(org.a2aproject.sdk.grpc.SubscribeToTaskRequestOrBuilder request) {
        org.a2aproject.sdk.grpc.SubscribeToTaskRequest proto = request instanceof org.a2aproject.sdk.grpc.SubscribeToTaskRequest
                ? (org.a2aproject.sdk.grpc.SubscribeToTaskRequest) request
                : ((org.a2aproject.sdk.grpc.SubscribeToTaskRequest.Builder) request).build();
        return new ObserveTaskParams(proto.getId(),
                proto.hasLastSeenGeneration() ? proto.getLastSeenGeneration() : null,
                A2ACommonFieldMapper.INSTANCE.emptyToNull(proto.getTenant()));
    }
}
