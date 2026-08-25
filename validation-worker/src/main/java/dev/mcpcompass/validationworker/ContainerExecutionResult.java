package dev.mcpcompass.validationworker;

record ContainerExecutionResult(Outcome outcome, Integer exitCode, String output) {
    static ContainerExecutionResult completed(String output) {
        return new ContainerExecutionResult(Outcome.COMPLETED, 0, output);
    }

    static ContainerExecutionResult observedRunning(String output) {
        return new ContainerExecutionResult(Outcome.OBSERVED_RUNNING, null, output);
    }

    static ContainerExecutionResult exited(int exitCode, String output) {
        return new ContainerExecutionResult(Outcome.FAILED, exitCode, output);
    }

    static ContainerExecutionResult timedOut(String output) {
        return new ContainerExecutionResult(Outcome.TIMED_OUT, null, output);
    }

    boolean completed() {
        return outcome == Outcome.COMPLETED;
    }

    boolean observedRunning() {
        return outcome == Outcome.OBSERVED_RUNNING;
    }

    String failureSummary() {
        String detail = output == null || output.isBlank() ? "no container output" : output.strip();
        if (outcome == Outcome.TIMED_OUT) {
            return "Container did not complete protocol validation within the observation window: " + detail;
        }
        return "Container exited unsuccessfully (exit " + exitCode + "): " + detail;
    }

    enum Outcome {
        COMPLETED,
        OBSERVED_RUNNING,
        FAILED,
        TIMED_OUT
    }
}
