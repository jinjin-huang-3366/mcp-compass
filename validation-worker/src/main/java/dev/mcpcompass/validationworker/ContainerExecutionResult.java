package dev.mcpcompass.validationworker;

record ContainerExecutionResult(boolean observedRunning, Integer exitCode, String output) {
    static ContainerExecutionResult observedRunning(String output) {
        return new ContainerExecutionResult(true, null, output);
    }

    static ContainerExecutionResult exited(int exitCode, String output) {
        return new ContainerExecutionResult(false, exitCode, output);
    }

    String failureSummary() {
        String detail = output == null || output.isBlank() ? "no container output" : output.strip();
        return "Container exited before the startup window (exit " + exitCode + "): " + detail;
    }
}
