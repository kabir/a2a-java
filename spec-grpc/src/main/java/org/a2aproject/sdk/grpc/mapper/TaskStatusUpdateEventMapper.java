package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps task status events while preserving the received generation stamp. */
@Mapper(config = A2AProtoMapperConfig.class, uses = {TaskStatusMapper.class, A2ACommonFieldMapper.class})
public interface TaskStatusUpdateEventMapper {

    TaskStatusUpdateEventMapper INSTANCE = A2AMappers.getMapper(TaskStatusUpdateEventMapper.class);

    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "metadataToProto")
    org.a2aproject.sdk.grpc.TaskStatusUpdateEvent toProto(TaskStatusUpdateEvent domain);

    default TaskStatusUpdateEvent fromProto(org.a2aproject.sdk.grpc.TaskStatusUpdateEvent proto) {
        if (proto == null) {
            return null;
        }
        TaskStatus status = TaskStatusMapper.INSTANCE.fromProto(proto.getStatus());
        return new TaskStatusUpdateEvent(proto.getTaskId(), status, proto.getContextId(),
                A2ACommonFieldMapper.INSTANCE.metadataFromProto(proto.getMetadata()), proto.getGeneration());
    }
}
