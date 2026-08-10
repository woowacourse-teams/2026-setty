package setty.estimate.application.command;

public record RecordManualNotificationCommand(
        String messageContent,
        boolean transportFeasible
) {
}
