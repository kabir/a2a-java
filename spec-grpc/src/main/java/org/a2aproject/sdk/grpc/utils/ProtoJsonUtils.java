package org.a2aproject.sdk.grpc.utils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MessageOrBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.util.Assert;
import org.a2aproject.sdk.util.HtmlEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * Protobuf-to-JSON serialization without HTML escaping.
 * <p>
 * {@link JsonFormat#printer()} delegates to Gson's {@link com.google.gson.stream.JsonWriter}
 * which HTML-escapes {@code <}, {@code >}, and {@code &} by default. This utility
 * removes those escape sequences via {@link HtmlEscapeUtils#removeHtmlEscaping(String)}.
 */
public final class ProtoJsonUtils {

    private ProtoJsonUtils() {
    }

    /**
     * Serializes a protobuf message to JSON using the supplied printer,
     * then removes HTML escaping.
     *
     * @param printer the configured {@link JsonFormat.Printer} (callers choose options
     *                such as {@code alwaysPrintFieldsWithNoPresence()} or
     *                {@code omittingInsignificantWhitespace()})
     * @param proto   the protobuf message to serialize, or {@code null}
     * @return JSON string without HTML-escaped characters, or empty string if proto is null
     */
    public static String toJson(JsonFormat.Printer printer, @Nullable MessageOrBuilder proto) throws InvalidProtocolBufferException {
        Assert.checkNotNullParam("printer", printer);
        if (proto == null) {
            return "";
        }
        return HtmlEscapeUtils.removeHtmlEscaping(printer.print(proto));
    }

    /** Serializes a protobuf response while omitting Phase 2 fields from legacy JSON contracts. */
    public static String toJsonForVersion(JsonFormat.Printer printer, @Nullable MessageOrBuilder proto,
            String protocolVersion) throws InvalidProtocolBufferException {
        if (!"1.0".equals(protocolVersion) && !"1.1".equals(protocolVersion)) {
            throw new IllegalArgumentException("Unsupported protocol version for protobuf JSON encoding: "
                    + protocolVersion);
        }
        String json = toJson(printer, proto);
        if (proto == null || "1.1".equals(protocolVersion)) {
            return json;
        }
        JsonElement tree = JsonParser.parseString(json);
        stripLegacyFields(tree, proto.getDescriptorForType());
        return HtmlEscapeUtils.removeHtmlEscaping(tree.toString());
    }

    private static void stripLegacyFields(JsonElement node, Descriptor descriptor) {
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject object = node.getAsJsonObject();
        for (FieldDescriptor field : descriptor.getFields()) {
            String name = field.getJsonName();
            if (isPhase2Field(descriptor.getName(), field.getName())) {
                object.remove(name);
            } else if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE && object.has(name)) {
                JsonElement child = object.get(name);
                if (field.isRepeated() && child.isJsonArray()) {
                    JsonArray array = child.getAsJsonArray();
                    for (JsonElement item : array) {
                        stripLegacyFields(item, field.getMessageType());
                    }
                } else {
                    stripLegacyFields(child, field.getMessageType());
                }
            }
        }
    }

    private static boolean isPhase2Field(String messageName, String fieldName) {
        return switch (messageName) {
            case "Task" -> fieldName.equals("generation") || fieldName.equals("timeline");
            case "Artifact" -> fieldName.equals("start_generation") || fieldName.equals("end_generation")
                    || fieldName.equals("role");
            case "TaskStatusUpdateEvent", "TaskArtifactUpdateEvent", "TaskMessageUpdateEvent" ->
                    fieldName.equals("generation");
            default -> false;
        };
    }
}
