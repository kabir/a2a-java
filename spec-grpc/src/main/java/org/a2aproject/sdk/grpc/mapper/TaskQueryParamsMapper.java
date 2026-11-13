package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.TaskQueryParams;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps get-task requests while preserving optional cursor and timeline-limit presence. */
@Mapper(config = A2AProtoMapperConfig.class, uses = A2ACommonFieldMapper.class)
public interface TaskQueryParamsMapper {

    TaskQueryParamsMapper INSTANCE = A2AMappers.getMapper(TaskQueryParamsMapper.class);

    @BeanMapping(builder = @Builder(buildMethod = "build"))
    @Mapping(target = "historyLength", expression = "java(proto.hasHistoryLength() ? proto.getHistoryLength() : null)")
    @Mapping(target = "currentGeneration", expression = "java(proto.hasCurrentGeneration() ? proto.getCurrentGeneration() : null)")
    @Mapping(target = "timelineLength", expression = "java(proto.hasTimelineLength() ? proto.getTimelineLength() : null)")
    TaskQueryParams fromProto(org.a2aproject.sdk.grpc.GetTaskRequest proto);

    @Mapping(target = "tenant", source = "tenant", conditionExpression = "java(domain.tenant() != null)")
    @Mapping(target = "historyLength", source = "historyLength", conditionExpression = "java(domain.historyLength() != null)")
    @Mapping(target = "currentGeneration", source = "currentGeneration", conditionExpression = "java(domain.currentGeneration() != null)")
    @Mapping(target = "timelineLength", source = "timelineLength", conditionExpression = "java(domain.timelineLength() != null)")
    org.a2aproject.sdk.grpc.GetTaskRequest toProto(TaskQueryParams domain);
}
