package setty.estimate.application.query;

public record ManualNotificationResult(
        String messageContent,
        boolean transportFeasible
) {
}
