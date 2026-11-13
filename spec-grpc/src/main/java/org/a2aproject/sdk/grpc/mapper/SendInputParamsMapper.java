package org.a2aproject.sdk.grpc.mapper;

import java.util.ArrayList;
import java.util.List;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.SendInputParams;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.mapstruct.Mapper;

/** Maps companion send input while ignoring client-supplied artifact generations. */
@Mapper(config = A2AProtoMapperConfig.class,
        uses = {MessageMapper.class, MessageSendConfigurationMapper.class, TaskArtifactUpdateEventMapper.class,
                A2ACommonFieldMapper.class})
public interface SendInputParamsMapper {

    SendInputParamsMapper INSTANCE = A2AMappers.getMapper(SendInputParamsMapper.class);

    default org.a2aproject.sdk.grpc.SendMessageRequest toProto(SendInputParams domain) {
        org.a2aproject.sdk.grpc.SendMessageRequest.Builder builder = org.a2aproject.sdk.grpc.SendMessageRequest.newBuilder();
        if (domain.message() != null) {
            builder.setMessage(MessageMapper.INSTANCE.toProto(domain.message()));
        }
        if (domain.configuration() != null) {
            builder.setConfiguration(MessageSendConfigurationMapper.INSTANCE.toProto(domain.configuration()));
        }
        if (domain.metadata() != null) {
            builder.setMetadata(A2ACommonFieldMapper.INSTANCE.metadataToProto(domain.metadata()));
        }
        if (domain.tenant() != null) {
            builder.setTenant(domain.tenant());
        }
        List<org.a2aproject.sdk.grpc.TaskArtifactUpdateEvent> updates = new ArrayList<>(domain.artifactUpdates().size());
        for (TaskArtifactUpdateEvent update : domain.artifactUpdates()) {
            updates.add(TaskArtifactUpdateEventMapper.INSTANCE.toProto(
                    TaskArtifactUpdateEvent.builder(update).generation(0).build()));
        }
        return builder.addAllArtifactUpdates(updates).build();
    }

    default SendInputParams fromProto(org.a2aproject.sdk.grpc.SendMessageRequestOrBuilder request) {
        org.a2aproject.sdk.grpc.SendMessageRequest proto = request instanceof org.a2aproject.sdk.grpc.SendMessageRequest
                ? (org.a2aproject.sdk.grpc.SendMessageRequest) request
                : ((org.a2aproject.sdk.grpc.SendMessageRequest.Builder) request).build();
        Message message = proto.hasMessage() ? MessageMapper.INSTANCE.fromProto(proto.getMessage()) : null;
        List<TaskArtifactUpdateEvent> updates = new ArrayList<>(proto.getArtifactUpdatesCount());
        for (org.a2aproject.sdk.grpc.TaskArtifactUpdateEvent update : proto.getArtifactUpdatesList()) {
            TaskArtifactUpdateEvent decoded = TaskArtifactUpdateEventMapper.INSTANCE.fromProto(update);
            updates.add(TaskArtifactUpdateEvent.builder(decoded).generation(0).build());
        }
        return new SendInputParams(message, updates,
                proto.hasConfiguration() ? MessageSendConfigurationMapper.INSTANCE.fromProto(proto.getConfiguration()) : null,
                A2ACommonFieldMapper.INSTANCE.metadataFromProto(proto.getMetadata()),
                A2ACommonFieldMapper.INSTANCE.emptyToNull(proto.getTenant()));
    }
}
