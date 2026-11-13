package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.Task;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps complete task snapshots, including the versioned timeline. */
@Mapper(config = A2AProtoMapperConfig.class,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        uses = {TaskStatusMapper.class, ArtifactMapper.class, MessageMapper.class, TimelineEntryMapper.class,
                A2ACommonFieldMapper.class})
public interface TaskMapper {

    TaskMapper INSTANCE = A2AMappers.getMapper(TaskMapper.class);

    @Mapping(target = "id", source = "id", conditionExpression = "java(domain.id() != null)")
    @Mapping(target = "contextId", source = "contextId", conditionExpression = "java(domain.contextId() != null)")
    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "metadataToProto")
    org.a2aproject.sdk.grpc.Task toProto(Task domain);

    @BeanMapping(builder = @Builder(buildMethod = "build"))
    @Mapping(target = "id", source = "id", qualifiedByName = "emptyToNull")
    @Mapping(target = "contextId", source = "contextId", qualifiedByName = "emptyToNull")
    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "metadataFromProto")
    Task fromProto(org.a2aproject.sdk.grpc.Task proto);
}
