// GOOD: illustrative caller; reportTasks is an injected Spring proxy.
try {
    reportTasks.render(reportId).whenComplete((result, failure) -> {
        if (failure != null) recordFailure(reportId, failure);
        else recordResult(reportId, result);
    });
} catch (TaskRejectedException overloaded) {
    rejectSubmission(reportId); // map to the existing retryable API contract
}
// This is process-local work, not durable acceptance. A callback also needs its own error policy.
