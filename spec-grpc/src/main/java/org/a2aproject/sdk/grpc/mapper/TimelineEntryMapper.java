package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.TimelineEntry;
import org.mapstruct.Mapper;

/** Maps known timeline entry variants. Unknown variants are filtered by the versioned decoder. */
@Mapper(config = A2AProtoMapperConfig.class, uses = {MessageMapper.class, TaskStatusMapper.class})
public interface TimelineEntryMapper {

    TimelineEntryMapper INSTANCE = A2AMappers.getMapper(TimelineEntryMapper.class);

    default org.a2aproject.sdk.grpc.TimelineEntry toProto(TimelineEntry domain) {
        org.a2aproject.sdk.grpc.TimelineEntry.Builder builder = org.a2aproject.sdk.grpc.TimelineEntry.newBuilder()
                .setGeneration(domain.generation());
        if (domain.entry() instanceof TimelineEntry.MessageValue message) {
            builder.setMessage(MessageMapper.INSTANCE.toProto(message.message()));
        } else if (domain.entry() instanceof TimelineEntry.StatusValue status) {
            builder.setStatus(TaskStatusMapper.INSTANCE.toProto(status.status()));
        } else {
            throw new IllegalArgumentException("Unknown timeline entry payload");
        }
        return builder.build();
    }

    default TimelineEntry fromProto(org.a2aproject.sdk.grpc.TimelineEntry proto) {
        TimelineEntry.Payload payload = switch (proto.getEntryCase()) {
            case MESSAGE -> new TimelineEntry.MessageValue(MessageMapper.INSTANCE.fromProto(proto.getMessage()));
            case STATUS -> new TimelineEntry.StatusValue(TaskStatusMapper.INSTANCE.fromProto(proto.getStatus()));
            case ENTRY_NOT_SET -> throw new IllegalArgumentException("TimelineEntry payload oneof field not set");
        };
        return new TimelineEntry(proto.getGeneration(), payload);
    }
}
