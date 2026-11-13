package phase2probe;

import java.util.List;

import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class LegacyCaller {
    private LegacyCaller() {
    }

    public static void main(String[] args) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("legacy caller")))
                .build();
        MessageSendParams params = MessageSendParams.builder().message(message).build();
        Task task = new Task("task", "context", new TaskStatus(TaskState.TASK_STATE_SUBMITTED), null, null, null);

        if (params.message().parts().size() != 1 || !task.id().equals("task")) {
            throw new AssertionError("Legacy constructor/accessor contract changed");
        }
        System.out.println("Task record components=" + Task.class.getRecordComponents().length);
    }
}
