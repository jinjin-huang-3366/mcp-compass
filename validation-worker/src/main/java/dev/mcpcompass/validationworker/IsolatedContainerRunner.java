package dev.mcpcompass.validationworker;

interface IsolatedContainerRunner {
    ContainerExecutionResult execute(ContainerExecutionRequest request) throws Exception;
}
