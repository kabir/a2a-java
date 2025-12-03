package io.a2a.grpc.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper between {@link io.a2a.spec.SubscribeToTaskRequest} and {@link io.a2a.grpc.SubscribeToTaskRequest}.
 * <p>
 * The mapping handles the structural difference between domain and proto representations:
 * <ul>
 * <li>Domain: Full JSONRPC request with id, jsonrpc, method, and params (TaskIdParams)</li>
 * <li>Proto: Simple request with name field in format "tasks/{task_id}"</li>
 * </ul>
 * <p>
 * Uses {@link ResourceNameParser} to convert between task ID and resource name format.
 * The domain JSONRPC envelope fields (id, jsonrpc, method) are populated with defaults
 * by the Builder when converting from proto.
 */
@Mapper(config = A2AProtoMapperConfig.class, uses = {TaskIdParamsMapper.class})
public interface SubscribeToTaskRequestMapper {

    SubscribeToTaskRequestMapper INSTANCE = A2AMappers.getMapper(SubscribeToTaskRequestMapper.class);

    /**
     * Converts domain SubscribeToTaskRequest to proto SubscribeToTaskRequest.
     * Extracts the task ID from params and formats it as "tasks/{task_id}".
     *
     * @param domain the domain SubscribeToTaskRequest
     * @return the proto SubscribeToTaskRequest
     */
    @Mapping(target = "name", expression = "java(ResourceNameParser.defineTaskName(domain.getParams().id()))")
    io.a2a.grpc.SubscribeToTaskRequest toProto(io.a2a.spec.SubscribeToTaskRequest domain);

    /**
     * Converts proto SubscribeToTaskRequest to domain SubscribeToTaskRequest.
     * Extracts the task ID from the name field and creates a TaskIdParams.
     * The JSONRPC envelope fields (id, jsonrpc, method) are populated with defaults by the Builder.
     *
     * @param proto the proto SubscribeToTaskRequest
     * @return the domain SubscribeToTaskRequest
     */
    @BeanMapping(builder = @Builder(buildMethod = "build"))
    @Mapping(target = "params", expression = "java(new io.a2a.spec.TaskIdParams(ResourceNameParser.extractTaskId(proto.getName())))")
    @Mapping(target = "id", ignore = true) // Builder sets UUID default
    @Mapping(target = "jsonrpc", ignore = true) // Builder sets "2.0" default
    @Mapping(target = "method", ignore = true) // Builder sets "SubscribeToTask" default
    io.a2a.spec.SubscribeToTaskRequest fromProto(io.a2a.grpc.SubscribeToTaskRequest proto);
}
