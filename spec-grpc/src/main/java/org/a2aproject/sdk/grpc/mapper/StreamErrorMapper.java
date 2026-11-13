package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.StreamError;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps a local uplink rejection separately from task events. */
@Mapper(config = A2AProtoMapperConfig.class, uses = A2ACommonFieldMapper.class)
public interface StreamErrorMapper {

    StreamErrorMapper INSTANCE = A2AMappers.getMapper(StreamErrorMapper.class);

    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "metadataToProto")
    org.a2aproject.sdk.grpc.StreamError toProto(StreamError domain);

    @Mapping(target = "taskId", source = "taskId", qualifiedByName = "emptyToNull")
    @Mapping(target = "message", source = "message", qualifiedByName = "emptyToNull")
    @Mapping(target = "referenceId", source = "referenceId", qualifiedByName = "emptyToNull")
    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "metadataFromProto")
    StreamError fromProto(org.a2aproject.sdk.grpc.StreamError proto);
}
