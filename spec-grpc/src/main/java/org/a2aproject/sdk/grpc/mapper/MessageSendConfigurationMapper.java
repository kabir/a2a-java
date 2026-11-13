package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps send configuration while distinguishing absent optional values from explicit zero. */
@Mapper(config = A2AProtoMapperConfig.class,
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        uses = {TaskPushNotificationConfigMapper.class, A2ACommonFieldMapper.class})
public interface MessageSendConfigurationMapper {

    MessageSendConfigurationMapper INSTANCE = A2AMappers.getMapper(MessageSendConfigurationMapper.class);

    @Mapping(target = "historyLength", source = "historyLength", conditionExpression = "java(domain.historyLength() != null)")
    @Mapping(target = "ifGenerationMatch", source = "ifGenerationMatch", conditionExpression = "java(domain.ifGenerationMatch() != null)")
    @Mapping(target = "timelineLength", source = "timelineLength", conditionExpression = "java(domain.timelineLength() != null)")
    @Mapping(target = "taskPushNotificationConfig", source = "taskPushNotificationConfig",
            conditionExpression = "java(domain.taskPushNotificationConfig() != null)")
    org.a2aproject.sdk.grpc.SendMessageConfiguration toProto(MessageSendConfiguration domain);

    @BeanMapping(builder = @Builder(buildMethod = "build"))
    @Mapping(target = "acceptedOutputModes",
            expression = "java(org.a2aproject.sdk.grpc.mapper.A2ACommonFieldMapper.INSTANCE.emptyListToNull(proto.getAcceptedOutputModesList()))")
    @Mapping(target = "historyLength", expression = "java(proto.hasHistoryLength() ? proto.getHistoryLength() : null)")
    @Mapping(target = "ifGenerationMatch", expression = "java(proto.hasIfGenerationMatch() ? proto.getIfGenerationMatch() : null)")
    @Mapping(target = "timelineLength", expression = "java(proto.hasTimelineLength() ? proto.getTimelineLength() : null)")
    MessageSendConfiguration fromProto(org.a2aproject.sdk.grpc.SendMessageConfiguration proto);
}
