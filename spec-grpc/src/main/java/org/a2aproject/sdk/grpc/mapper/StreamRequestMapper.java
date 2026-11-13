package org.a2aproject.sdk.grpc.mapper;

import org.a2aproject.sdk.spec.StreamRequest;
import org.mapstruct.Mapper;

/** Maps the 1.1 stream input envelope. */
@Mapper(config = A2AProtoMapperConfig.class,
        uses = {SendInputParamsMapper.class, ObserveTaskParamsMapper.class})
public interface StreamRequestMapper {

    StreamRequestMapper INSTANCE = A2AMappers.getMapper(StreamRequestMapper.class);

    default org.a2aproject.sdk.grpc.StreamRequest toProto(StreamRequest domain) {
        org.a2aproject.sdk.grpc.StreamRequest.Builder builder = org.a2aproject.sdk.grpc.StreamRequest.newBuilder();
        if (domain.tenant() != null) {
            builder.setTenant(domain.tenant());
        }
        if (domain.payload() instanceof StreamRequest.Send send) {
            builder.setSendMessage(SendInputParamsMapper.INSTANCE.toProto(send.sendMessage()));
        } else if (domain.payload() instanceof StreamRequest.Subscribe subscribe) {
            builder.setSubscribe(ObserveTaskParamsMapper.INSTANCE.toProto(subscribe.subscribe()));
        } else {
            throw new IllegalArgumentException("Unknown stream request payload");
        }
        return builder.build();
    }

    default StreamRequest fromProto(org.a2aproject.sdk.grpc.StreamRequest proto) {
        StreamRequest.Payload payload = switch (proto.getPayloadCase()) {
            case SEND_MESSAGE -> new StreamRequest.Send(SendInputParamsMapper.INSTANCE.fromProto(proto.getSendMessage()));
            case SUBSCRIBE -> new StreamRequest.Subscribe(ObserveTaskParamsMapper.INSTANCE.fromProto(proto.getSubscribe()));
            case PAYLOAD_NOT_SET -> throw new IllegalArgumentException("StreamRequest payload oneof field not set");
        };
        return new StreamRequest(A2ACommonFieldMapper.INSTANCE.emptyToNull(proto.getTenant()), payload);
    }
}
