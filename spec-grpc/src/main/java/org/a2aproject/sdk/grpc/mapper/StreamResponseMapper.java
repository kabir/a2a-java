package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskMessageUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.mapstruct.Mapper;

/** Maps task events; local StreamError outcomes are handled by StreamDecoder. */
@Mapper(config = A2AProtoMapperConfig.class,
        uses = {TaskMapper.class, MessageMapper.class, TaskStatusUpdateEventMapper.class,
                TaskArtifactUpdateEventMapper.class, TaskMessageUpdateEventMapper.class})
public interface StreamResponseMapper {

    StreamResponseMapper INSTANCE = A2AMappers.getMapper(StreamResponseMapper.class);

    default org.a2aproject.sdk.grpc.StreamResponse toProto(StreamingEventKind domain) {
        if (domain == null) {
            return null;
        }
        return switch (domain.kind()) {
            case Task.STREAMING_EVENT_ID -> org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                    .setTask(TaskMapper.INSTANCE.toProto((Task) domain)).build();
            case Message.STREAMING_EVENT_ID -> org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                    .setMessage(MessageMapper.INSTANCE.toProto((Message) domain)).build();
            case TaskStatusUpdateEvent.STREAMING_EVENT_ID -> org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                    .setStatusUpdate(TaskStatusUpdateEventMapper.INSTANCE.toProto((TaskStatusUpdateEvent) domain)).build();
            case TaskArtifactUpdateEvent.STREAMING_EVENT_ID -> org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                    .setArtifactUpdate(TaskArtifactUpdateEventMapper.INSTANCE.toProto((TaskArtifactUpdateEvent) domain)).build();
            case TaskMessageUpdateEvent.STREAMING_EVENT_ID -> org.a2aproject.sdk.grpc.StreamResponse.newBuilder()
                    .setMessageUpdate(TaskMessageUpdateEventMapper.INSTANCE.toProto((TaskMessageUpdateEvent) domain)).build();
            default -> throw new IllegalArgumentException("Unknown StreamingEventKind type: " + domain.getClass().getName());
        };
    }

    /** Legacy strict conversion. StreamError is not a task event and must use the outcome decoder. */
    default StreamingEventKind fromProto(org.a2aproject.sdk.grpc.StreamResponse proto) {
        if (proto == null) {
            return null;
        }
        return switch (proto.getPayloadCase()) {
            case TASK -> TaskMapper.INSTANCE.fromProto(proto.getTask());
            case MESSAGE -> MessageMapper.INSTANCE.fromProto(proto.getMessage());
            case STATUS_UPDATE -> TaskStatusUpdateEventMapper.INSTANCE.fromProto(proto.getStatusUpdate());
            case ARTIFACT_UPDATE -> TaskArtifactUpdateEventMapper.INSTANCE.fromProto(proto.getArtifactUpdate());
            case MESSAGE_UPDATE -> TaskMessageUpdateEventMapper.INSTANCE.fromProto(proto.getMessageUpdate());
            case ERROR -> throw new IllegalArgumentException("StreamError requires the versioned stream outcome decoder");
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException("StreamResponse payload oneof field not set");
        };
    }
}
