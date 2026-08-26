package dev.mcpcompass.validationworker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class DockerCliContainerRunner implements IsolatedContainerRunner {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5);

    private final DockerContainerCommandFactory commands;

    DockerCliContainerRunner(String dockerCli) {
        this.commands = new DockerContainerCommandFactory(dockerCli);
    }

    @Override
    public ContainerExecutionResult execute(ContainerExecutionRequest request) throws Exception {
        String containerName = "mcp-compass-validation-" + UUID.randomUUID();
        long deadlineNanos = System.nanoTime() + request.sandboxPolicy().wallTimeLimit().toNanos();
        try {
            CommandResult created = runToCompletion(
                    commands.create(containerName, request),
                    remaining(deadlineNanos, COMMAND_TIMEOUT)
            );
            if (created.exitCode() != 0) {
                return ContainerExecutionResult.exited(created.exitCode(), created.output());
            }

            Process attached = start(commands.startAttached(containerName));
            OutputCapture capture = new OutputCapture(attached.getInputStream(), MAX_OUTPUT_BYTES);
            Thread reader = Thread.ofVirtual().start(capture);
            Duration observationTime = remaining(deadlineNanos, request.observationWindow());
            boolean exited = attached.waitFor(observationTime.toMillis(), TimeUnit.MILLISECONDS);
            if (exited) {
                reader.join(Duration.ofSeconds(2));
                if (attached.exitValue() == 0
                        && request.expectedOutcome() == ContainerExecutionRequest.ExpectedOutcome.SUCCESSFUL_EXIT) {
                    return ContainerExecutionResult.completed(capture.value());
                }
                return ContainerExecutionResult.exited(attached.exitValue(), capture.value());
            }
            if (observationTime.compareTo(request.observationWindow()) < 0) {
                throw new IOException("Container workload exceeded its wall-time limit");
            }
            if (request.expectedOutcome() == ContainerExecutionRequest.ExpectedOutcome.SUCCESSFUL_EXIT) {
                return ContainerExecutionResult.timedOut(capture.value());
            }
            return ContainerExecutionResult.observedRunning(capture.value());
        } finally {
            try {
                runToCompletion(commands.removeForced(containerName), Duration.ofSeconds(20));
            } catch (Exception ignored) {
                // The container may already be absent. Cleanup is best-effort and never masks the execution result.
            }
        }
    }

    private static Duration remaining(long deadlineNanos, Duration maximum) throws IOException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new IOException("Container workload exceeded its wall-time limit");
        }
        return Duration.ofNanos(Math.min(remainingNanos, maximum.toNanos()));
    }

    private static CommandResult runToCompletion(List<String> command, Duration timeout) throws Exception {
        Process process = start(command);
        OutputCapture capture = new OutputCapture(process.getInputStream(), MAX_OUTPUT_BYTES);
        Thread reader = Thread.ofVirtual().start(capture);
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException("Container runtime command timed out: " + command.get(1));
        }
        reader.join(Duration.ofSeconds(2));
        return new CommandResult(process.exitValue(), capture.value());
    }

    private static Process start(List<String> command) throws IOException {
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
    }

    private record CommandResult(int exitCode, String output) {
    }

    private static final class OutputCapture implements Runnable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private OutputCapture(InputStream input, int limit) {
            this.input = input;
            this.limit = limit;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            try (input) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    int remaining = limit - bytes.size();
                    if (remaining > 0) {
                        append(buffer, Math.min(count, remaining));
                    }
                }
            } catch (IOException ignored) {
                // Forced container cleanup commonly closes the attached stream.
            }
        }

        synchronized String value() {
            return bytes.toString(StandardCharsets.UTF_8);
        }

        private synchronized void append(byte[] buffer, int count) {
            bytes.write(buffer, 0, count);
        }
    }
}
