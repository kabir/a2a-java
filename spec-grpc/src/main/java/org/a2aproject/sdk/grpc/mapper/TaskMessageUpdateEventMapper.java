package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps the 1.1 message update event. */
@Mapper(config = A2AProtoMapperConfig.class, uses = {MessageMapper.class, A2ACommonFieldMapper.class})
public interface TaskMessageUpdateEventMapper {

    TaskMessageUpdateEventMapper INSTANCE = A2AMappers.getMapper(TaskMessageUpdateEventMapper.class);

    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "metadataToProto")
    org.a2aproject.sdk.grpc.TaskMessageUpdateEvent toProto(TaskMessageUpdateEvent domain);

    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "metadataFromProto")
    TaskMessageUpdateEvent fromProto(org.a2aproject.sdk.grpc.TaskMessageUpdateEvent proto);
}
