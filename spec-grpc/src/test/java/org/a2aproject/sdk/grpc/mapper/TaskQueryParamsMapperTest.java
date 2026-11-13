package org.a2aproject.sdk.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.a2aproject.sdk.grpc.GetTaskRequest;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.junit.jupiter.api.Test;

class TaskQueryParamsMapperTest {

    @Test
    void toProtoWithoutTenantPreservesCurrentGenerationAndTimelineLength() {
        TaskQueryParams params = TaskQueryParams.builder()
                .id("task-123")
                .currentGeneration(12L)
                .timelineLength(4)
                .build();

        GetTaskRequest request = TaskQueryParamsMapper.INSTANCE.toProto(params);

        assertEquals("task-123", request.getId());
        assertEquals("", request.getTenant());
        assertEquals(12L, request.getCurrentGeneration());
        assertEquals(4, request.getTimelineLength());
    }
}
