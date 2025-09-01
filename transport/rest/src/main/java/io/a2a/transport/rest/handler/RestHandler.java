package io.a2a.transport.rest.handler;

import static io.a2a.server.util.async.AsyncUtils.createTubeConfig;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.a2a.grpc.utils.ProtoUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.a2a.server.PublicAgentCard;
import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.AgentCard;
import io.a2a.spec.ContentTypeNotSupportedError;
import io.a2a.spec.DeleteTaskPushNotificationConfigParams;
import io.a2a.spec.EventKind;
import io.a2a.spec.GetTaskPushNotificationConfigParams;
import io.a2a.spec.InternalError;
import io.a2a.spec.InvalidAgentResponseError;
import io.a2a.spec.InvalidParamsError;
import io.a2a.spec.InvalidRequestError;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.ListTaskPushNotificationConfigParams;
import io.a2a.spec.MethodNotFoundError;
import io.a2a.spec.PushNotificationNotSupportedError;
import io.a2a.spec.StreamingEventKind;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskNotCancelableError;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskPushNotificationConfig;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.UnsupportedOperationError;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import mutiny.zero.ZeroPublisher;

@ApplicationScoped
public class RestHandler {

    private static final Logger log = Logger.getLogger(RestHandler.class.getName());
    private static final Pattern GET_TASK_PATTERN = Pattern.compile("^/v1/tasks/([^/]+)$");
    private static final Pattern CANCEL_TASK_PATTERN = Pattern.compile("^/v1/tasks/([^/]+):cancel$");
    public static final Pattern RESUBSCRIBE_TASK_PATTERN = Pattern.compile("^/v1/tasks/([^/]+):subscribe$");
    private static final Pattern GET_PUSH_NOTIFICATION_CONFIG_PATTERN = Pattern.compile("^/v1/tasks/([^/]+)/pushNotificationConfigs/([^/]+)$");
    private static final Pattern LIST_PUSH_NOTIFICATION_CONFIG_PATTERN = Pattern.compile("^/v1/tasks/([^/]+)/pushNotificationConfigs$");
    private static final Pattern DELETE_PUSH_NOTIFICATION_CONFIG_PATTERN = Pattern.compile("^/v1/tasks/([^/]+)/pushNotificationConfigs/([^/]+)$");
    public static final String SEND_MESSAGE_STREAMING_PATH = "/v1/message:stream";

    private AgentCard agentCard;
    private RequestHandler requestHandler;

    protected RestHandler() {
        // For CDI
    }

    @Inject
    public RestHandler(@PublicAgentCard AgentCard agentCard, RequestHandler requestHandler) {
        this.agentCard = agentCard;
        this.requestHandler = requestHandler;
    }

    public HTTPRestResponse handleRequest(String method, String path, String body, ServerCallContext context) {
        try {
            switch (method.toUpperCase(Locale.US)) {
                case "GET":
                    return handleGetRequest(path, context);
                case "POST":
                    return handlePostRequest(path, body, context);
                case "PUT":
                    return handlePutRequest(path, body, context);
                case "DELETE":
                    return handleDeleteRequest(path, context);
                default:
                    return createErrorResponse(405, new MethodNotFoundError());
            }
        } catch (JSONRPCError e) {
            log.log(Level.SEVERE, "JSONRPC error occurred during request handling", e);
            return createErrorResponse(e);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unexpected error occurred during request handling", e);
            return createErrorResponse(new InternalError(e.getMessage()));
        }
    }

    private HTTPRestResponse handleGetRequest(String path, ServerCallContext context) throws JSONRPCError {
        if ("/v1/card".equals(path)) {
            return createSuccessResponse(200, io.a2a.grpc.AgentCard.newBuilder(ProtoUtils.ToProto.agentCard(agentCard)));
        }

        Matcher taskMatcher = GET_TASK_PATTERN.matcher(path);
        if (taskMatcher.matches()) {
            return getTask(taskMatcher.group(1), context);
        }

        Matcher pushConfigMatcher = GET_PUSH_NOTIFICATION_CONFIG_PATTERN.matcher(path);
        if (pushConfigMatcher.matches()) {
            return getTaskPushNotificationConfiguration(pushConfigMatcher.group(1), pushConfigMatcher.group(2), context);
        }

        Matcher listPushConfigMatcher = LIST_PUSH_NOTIFICATION_CONFIG_PATTERN.matcher(path);
        if (listPushConfigMatcher.matches()) {
            return listTaskPushNotificationConfigurations(listPushConfigMatcher.group(1), context);
        }

        throw new MethodNotFoundError();
    }

    private HTTPRestResponse handlePostRequest(String path, String body, ServerCallContext context) throws JSONRPCError {
        if ("/v1/message:send".equals(path)) {
            return sendMessage(body, context);
        }

        if (SEND_MESSAGE_STREAMING_PATH.equals(path)) {
            return sendStreamingMessage(body, context);
        }

        Matcher cancelMatcher = CANCEL_TASK_PATTERN.matcher(path);
        if (cancelMatcher.matches()) {
            return cancelTask(cancelMatcher.group(1), context);
        }

        Matcher resubscribeMatcher = RESUBSCRIBE_TASK_PATTERN.matcher(path);
        if (resubscribeMatcher.matches()) {
            return resubscribeTask(resubscribeMatcher.group(1), context);
        }

        Matcher listPushConfigMatcher = LIST_PUSH_NOTIFICATION_CONFIG_PATTERN.matcher(path);
        if (listPushConfigMatcher.matches()) {
            return setTaskPushNotificationConfiguration(listPushConfigMatcher.group(1), body, context);
        }

        throw new MethodNotFoundError();
    }

    public HTTPRestResponse sendMessage(String body, ServerCallContext context) {
        io.a2a.grpc.SendMessageRequest.Builder request = io.a2a.grpc.SendMessageRequest.newBuilder();
        parseRequestBody(body, request);
        EventKind result = requestHandler.onMessageSend(ProtoUtils.FromProto.messageSendParams(request.build()), context);
        return createSuccessResponse(200, io.a2a.grpc.SendMessageResponse.newBuilder(ProtoUtils.ToProto.taskOrMessage(result)));
    }

    public HTTPRestStreamingResponse sendStreamingMessage(String body, ServerCallContext context) {
        if (!agentCard.capabilities().streaming()) {
            throw new InvalidRequestError("Streaming is not supported by the agent");
        }
        try {
            io.a2a.grpc.SendMessageRequest.Builder request = io.a2a.grpc.SendMessageRequest.newBuilder();
            parseRequestBody(body, request);
            Flow.Publisher<StreamingEventKind> publisher = requestHandler.onMessageSendStream(ProtoUtils.FromProto.messageSendParams(request.build()), context);
            return createStreamingResponse(publisher);
        } catch (JSONRPCError e) {
            return new HTTPRestStreamingResponse(ZeroPublisher.fromItems(new HTTPRestErrorResponse(e).toJson()));
        } catch (Throwable throwable) {
            return new HTTPRestStreamingResponse(ZeroPublisher.fromItems(new HTTPRestErrorResponse(new InternalError(throwable.getMessage())).toJson()));
        }
    }

    public HTTPRestResponse cancelTask(String taskId, ServerCallContext context) {
        TaskIdParams params = new TaskIdParams(taskId);
        Task task = requestHandler.onCancelTask(params, context);
        if (task != null) {
            return createSuccessResponse(200, io.a2a.grpc.Task.newBuilder(ProtoUtils.ToProto.task(task)));
        }
        throw new UnsupportedOperationError();
    }

    public HTTPRestResponse setTaskPushNotificationConfiguration(String taskId, String body, ServerCallContext context) {
        if (!agentCard.capabilities().pushNotifications()) {
            throw new PushNotificationNotSupportedError();
        }
        io.a2a.grpc.CreateTaskPushNotificationConfigRequest.Builder builder = io.a2a.grpc.CreateTaskPushNotificationConfigRequest.newBuilder();
        parseRequestBody(body, builder);
        TaskPushNotificationConfig result = requestHandler.onSetTaskPushNotificationConfig(ProtoUtils.FromProto.taskPushNotificationConfig(builder), context);
        return createSuccessResponse(201, io.a2a.grpc.TaskPushNotificationConfig.newBuilder(ProtoUtils.ToProto.taskPushNotificationConfig(result)));
    }

    public HTTPRestStreamingResponse resubscribeTask(String taskId, ServerCallContext context) {
        if (!agentCard.capabilities().streaming()) {
            throw new InvalidRequestError("Streaming is not supported by the agent");
        }
        try {
            TaskIdParams params = new TaskIdParams(taskId);
            Flow.Publisher<StreamingEventKind> publisher = requestHandler.onResubscribeToTask(params, context);
            return createStreamingResponse(publisher);
        } catch (JSONRPCError e) {
            return new HTTPRestStreamingResponse(ZeroPublisher.fromItems(new HTTPRestErrorResponse(e).toJson()));
        } catch (Throwable throwable) {
            return new HTTPRestStreamingResponse(ZeroPublisher.fromItems(new HTTPRestErrorResponse(new InternalError(throwable.getMessage())).toJson()));
        }
    }

    public HTTPRestResponse getTask(String taskId, ServerCallContext context) {
        TaskQueryParams params = new TaskQueryParams(taskId);
        Task task = requestHandler.onGetTask(params, context);
        if (task != null) {
            return createSuccessResponse(200, io.a2a.grpc.Task.newBuilder(ProtoUtils.ToProto.task(task)));
        }
        throw new TaskNotFoundError();
    }

    public HTTPRestResponse getTaskPushNotificationConfiguration(String taskId, String configId, ServerCallContext context) {
        if (!agentCard.capabilities().pushNotifications()) {
            throw new PushNotificationNotSupportedError();
        }
        GetTaskPushNotificationConfigParams params = new GetTaskPushNotificationConfigParams(taskId, configId);
        TaskPushNotificationConfig config = requestHandler.onGetTaskPushNotificationConfig(params, context);
        return createSuccessResponse(200, io.a2a.grpc.TaskPushNotificationConfig.newBuilder(ProtoUtils.ToProto.taskPushNotificationConfig(config)));
    }

    public HTTPRestResponse listTaskPushNotificationConfigurations(String taskId, ServerCallContext context) {
        if (!agentCard.capabilities().pushNotifications()) {
            throw new PushNotificationNotSupportedError();
        }
        ListTaskPushNotificationConfigParams params = new ListTaskPushNotificationConfigParams(taskId);
        List<TaskPushNotificationConfig> configs = requestHandler.onListTaskPushNotificationConfig(params, context);
        return createSuccessResponse(200, ProtoUtils.ToProto.listTaskPushNotificationConfigResponse(configs).toBuilder());
    }

    public HTTPRestResponse deleteTaskPushNotificationConfiguration(String taskId, String configId, ServerCallContext context) {
        if (!agentCard.capabilities().pushNotifications()) {
            throw new PushNotificationNotSupportedError();
        }
        DeleteTaskPushNotificationConfigParams params = new DeleteTaskPushNotificationConfigParams(taskId, configId);
        requestHandler.onDeleteTaskPushNotificationConfig(params, context);
        return createSuccessResponse(204, null);
    }

    private HTTPRestResponse handlePutRequest(String path, String body, ServerCallContext context) throws JSONRPCError {
        throw new MethodNotFoundError();
    }

    private HTTPRestResponse handleDeleteRequest(String path, ServerCallContext context) throws JSONRPCError {
        Matcher deleteMatcher = DELETE_PUSH_NOTIFICATION_CONFIG_PATTERN.matcher(path);
        if (deleteMatcher.matches()) {
            String taskId = deleteMatcher.group(1);
            String configId = deleteMatcher.group(2);
            return deleteTaskPushNotificationConfiguration(taskId, configId, context);
        }

        throw new MethodNotFoundError();
    }

    private void parseRequestBody(String body, com.google.protobuf.Message.Builder builder) throws JSONRPCError {
        try {
            if (body == null || body.trim().isEmpty()) {
                throw new InvalidParamsError("Request body is required");
            }
            JsonFormat.parser().merge(body, builder);
        } catch (InvalidProtocolBufferException e) {
            log.log(Level.FINE, "Error parsing JSON request body: {0}", body);
            log.log(Level.FINE, "Parse error details", e);
            throw new InvalidParamsError("Failed to parse request body: " + e.getMessage());
        }
    }

    private HTTPRestResponse createSuccessResponse(int statusCode, com.google.protobuf.Message.Builder builder) {
        try {
            String jsonBody = builder != null ? JsonFormat.printer().print(builder) : "";
            return new HTTPRestResponse(statusCode, "application/json", jsonBody);
        } catch (InvalidProtocolBufferException e) {
            return createErrorResponse(new InternalError("Failed to serialize response: " + e.getMessage()));
        }
    }

    public HTTPRestResponse createErrorResponse(JSONRPCError error) {
        int statusCode = mapErrorToHttpStatus(error);
        return createErrorResponse(statusCode, error);
    }

    private HTTPRestResponse createErrorResponse(int statusCode, JSONRPCError error) {
        String jsonBody = new HTTPRestErrorResponse(error).toJson();
        return new HTTPRestResponse(statusCode, "application/json", jsonBody);
    }

    private HTTPRestStreamingResponse createStreamingResponse(Flow.Publisher<StreamingEventKind> publisher) {
        return new HTTPRestStreamingResponse(convertToSendStreamingMessageResponse(publisher));
    }

    private Flow.Publisher<String> convertToSendStreamingMessageResponse(
            Flow.Publisher<StreamingEventKind> publisher) {
        // We can't use the normal convertingProcessor since that propagates any errors as an error handled
        // via Subscriber.onError() rather than as part of the SendStreamingResponse payload
        return ZeroPublisher.create(createTubeConfig(), tube -> {
            CompletableFuture.runAsync(() -> {
                publisher.subscribe(new Flow.Subscriber<StreamingEventKind>() {
                    Flow.Subscription subscription;

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(StreamingEventKind item) {
                        try {
                            String payload = JsonFormat.printer().omittingInsignificantWhitespace().print(ProtoUtils.ToProto.taskOrMessageStream(item));
                            System.out.println("############## Sending event " + payload);
                            tube.send(payload);
                            subscription.request(1);
                        } catch (InvalidProtocolBufferException ex) {
                            onError(ex);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        System.out.println("############## Sending error " + throwable);
                        throwable.printStackTrace();
                        if (throwable instanceof JSONRPCError jsonrpcError) {
                            tube.send(new HTTPRestErrorResponse(jsonrpcError).toJson());
                        } else {
                            tube.send(new HTTPRestErrorResponse(new InternalError(throwable.getMessage())).toJson());
                        }
                        onComplete();
                    }

                    @Override
                    public void onComplete() {
                        tube.complete();
                    }
                });
            });
        });
    }

    private int mapErrorToHttpStatus(JSONRPCError error) {
        if (error instanceof InvalidRequestError || error instanceof InvalidParamsError) {
            return 400;
        }
        if (error instanceof MethodNotFoundError || error instanceof TaskNotFoundError) {
            return 404;
        }
        if (error instanceof TaskNotCancelableError || error instanceof PushNotificationNotSupportedError
                || error instanceof UnsupportedOperationError) {
            return 501;
        }
        if (error instanceof ContentTypeNotSupportedError) {
            return 415;
        }
        if (error instanceof InternalError || error instanceof InvalidAgentResponseError) {
            return 500;
        }
        return 500;
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public static class HTTPRestResponse {

        private final int statusCode;
        private final String contentType;
        private final String body;

        public HTTPRestResponse(int statusCode, String contentType, String body) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getContentType() {
            return contentType;
        }

        public String getBody() {
            return body;
        }

        @Override
        public String toString() {
            return "HTTPRestResponse{" + "statusCode=" + statusCode + ", contentType=" + contentType + ", body=" + body + '}';
        }
    }

    public static class HTTPRestStreamingResponse extends HTTPRestResponse {

        private final Flow.Publisher<String> publisher;

        public HTTPRestStreamingResponse(Flow.Publisher<String> publisher) {
            super(200, "text/event-stream", null);
            this.publisher = publisher;
        }

        public Flow.Publisher<String> getPublisher() {
            return publisher;
        }
    }

    private static class HTTPRestErrorResponse {

        private final String error;
        private final String message;

        public HTTPRestErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public HTTPRestErrorResponse(JSONRPCError jsonRpcError) {
            this.error = jsonRpcError.getClass().getName();
            this.message = jsonRpcError.getMessage();
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public String toJson() {
            return "{\"error\": \"" + error + "\", \"message\": \"" + message + "\"}";
        }

        @Override
        public String toString() {
            return "HTTPRestErrorResponse{" + "error=" + error + ", message=" + message + '}';
        }
    }
}
