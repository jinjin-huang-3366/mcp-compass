package dev.mcpcompass.remote;

import dev.mcpcompass.registry.McpServerEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record SetupReadiness(List<InstallOption> options) {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_OPTIONS_PER_SOURCE = 10;
    private static final int MAX_INPUTS = 20;
    private static final Pattern INPUT_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]{0,63})}");

    public static SetupReadiness from(McpServerEntity server) {
        JsonNode declaration;
        try {
            declaration = RegistryRemoteConnectionSource.declaration(MAPPER.readTree(server.getRawMetadata()));
        } catch (RuntimeException malformedMetadata) {
            return new SetupReadiness(List.of());
        }
        List<InstallOption> options = new ArrayList<>();
        JsonNode remotes = declaration.path("remotes");
        if (remotes.isArray()) {
            int selected = RegistryRemoteConnectionSource.selectedRemoteIndex(remotes);
            for (int i = 0; i < Math.min(remotes.size(), MAX_OPTIONS_PER_SOURCE); i++) {
                options.add(remote(remotes.path(i), i, i == selected ? server : null));
            }
            if (selected >= MAX_OPTIONS_PER_SOURCE) {
                options.add(remote(remotes.path(selected), selected, server));
            }
        }
        JsonNode packages = declaration.path("packages");
        if (packages.isArray()) {
            for (int i = 0; i < Math.min(packages.size(), MAX_OPTIONS_PER_SOURCE); i++) {
                options.add(packageOption(packages.path(i), i));
            }
        }
        return new SetupReadiness(List.copyOf(options));
    }

    private static InstallOption remote(JsonNode remote, int index, McpServerEntity observed) {
        Transport transport = transport(remote.path("type"));
        Set<SetupInput> inputs = new LinkedHashSet<>();
        JsonNode variables = remote.path("variables");
        addTemplateInputs(remote.path("url"), variables, inputs);
        boolean authDeclared = false;
        JsonNode headers = remote.path("headers");
        if (headers.isArray()) {
            for (JsonNode header : headers) {
                if (header.path("isRequired").isBoolean() && header.path("isRequired").asBoolean()) {
                    String name = header.path("name").asText();
                    authDeclared |= header.path("isSecret").asBoolean() || authHeader(name);
                    addInput(header, InputKind.HEADER, inputs);
                    JsonNode headerVariables = header.hasNonNull("variables") ? header.path("variables") : variables;
                    addTemplateInputs(header.path("value"), headerVariables, inputs);
                    addTemplateInputs(header.path("default"), headerVariables, inputs);
                }
            }
        }
        String state = observed == null ? "" : observed.getRemoteConnectionState();
        Authentication auth = authDeclared ? Authentication.DECLARED_REQUIRED : Authentication.UNKNOWN;
        if (RemoteConnectionState.AUTH_REQUIRED.name().equals(state)) {
            boolean oauth = RemoteAuthenticationChallenge.oauthAdvertised(observed.getRemoteConnectionReason());
            auth = oauth ? Authentication.OAUTH_ADVERTISED : Authentication.REQUIRED;
            inputs.add(new SetupInput(oauth ? InputKind.OAUTH_AUTHORIZATION : InputKind.AUTHORIZATION, null));
        } else if (RemoteConnectionState.REACHABLE.name().equals(state)) {
            auth = authDeclared ? Authentication.DECLARATION_MISMATCH : Authentication.INITIALIZED_WITHOUT_AUTH;
        }
        Protocol protocol = switch (transport) {
            case STREAMABLE_HTTP -> {
                if (RemoteConnectionState.REACHABLE.name().equals(state)) {
                    yield Protocol.INITIALIZE_SUCCEEDED;
                }
                if (RemoteConnectionState.PROTOCOL_MISMATCH.name().equals(state)) {
                    yield Protocol.MISMATCH;
                }
                yield Protocol.UNVERIFIED;
            }
            case SSE, STDIO -> Protocol.UNSUPPORTED_BY_CHECK;
            case UNKNOWN -> Protocol.UNKNOWN;
        };
        return option(OptionSource.REMOTE, index, transport, auth, protocol, inputs);
    }

    private static InstallOption packageOption(JsonNode pkg, int index) {
        Transport transport = transport(pkg.path("transport").path("type"));
        Set<SetupInput> inputs = new LinkedHashSet<>();
        addRequiredInputs(pkg.path("environmentVariables"), InputKind.ENVIRONMENT_VARIABLE, inputs);
        addRequiredInputs(pkg.path("runtimeArguments"), InputKind.RUNTIME_ARGUMENT, inputs);
        addRequiredInputs(pkg.path("packageArguments"), InputKind.PACKAGE_ARGUMENT, inputs);
        Protocol protocol = transport == Transport.UNKNOWN ? Protocol.UNKNOWN : Protocol.UNVERIFIED;
        return option(OptionSource.PACKAGE, index, transport, Authentication.UNKNOWN, protocol, inputs);
    }

    private static InstallOption option(OptionSource source, int index, Transport transport, Authentication auth,
                                        Protocol protocol, Set<SetupInput> inputs) {
        return new InstallOption(source, index + 1, transport, auth, protocol, List.copyOf(inputs));
    }

    private static void addRequiredInputs(JsonNode declarations, InputKind kind, Set<SetupInput> inputs) {
        if (declarations.isArray()) {
            for (JsonNode input : declarations) {
                if (input.path("isRequired").isBoolean() && input.path("isRequired").asBoolean()) {
                    addInput(input, kind, inputs);
                }
            }
        }
    }

    private static void addInput(JsonNode declaration, InputKind kind, Set<SetupInput> inputs) {
        if (inputs.size() < MAX_INPUTS && !supplied(declaration)) {
            String name = declaration.path("name").asText();
            inputs.add(new SetupInput(kind, INPUT_NAME.matcher(name).matches() ? name : null));
        }
    }

    private static boolean supplied(JsonNode declaration) {
        return hasValue(declaration.path("value")) || hasValue(declaration.path("default"));
    }

    private static boolean hasValue(JsonNode value) {
        return (value.isString() && !value.stringValue().isBlank()) || value.isNumber() || value.isBoolean();
    }

    private static void addTemplateInputs(JsonNode value, JsonNode variables, Set<SetupInput> inputs) {
        if (!value.isString()) {
            return;
        }
        var variable = VARIABLE.matcher(value.stringValue());
        while (inputs.size() < MAX_INPUTS && variable.find()) {
            String name = variable.group(1);
            if (!supplied(variables.path(name))) {
                inputs.add(new SetupInput(InputKind.TEMPLATE_VARIABLE, name));
            }
        }
    }

    private static boolean authHeader(String name) {
        return "Authorization".equalsIgnoreCase(name) || "Proxy-Authorization".equalsIgnoreCase(name)
                || "X-API-Key".equalsIgnoreCase(name) || "API-Key".equalsIgnoreCase(name)
                || "Cookie".equalsIgnoreCase(name);
    }

    private static Transport transport(JsonNode type) {
        return switch (type.asText().toLowerCase(java.util.Locale.ROOT)) {
            case "streamable-http" -> Transport.STREAMABLE_HTTP;
            case "sse" -> Transport.SSE;
            case "stdio" -> Transport.STDIO;
            default -> Transport.UNKNOWN;
        };
    }

    public record InstallOption(OptionSource source, int position, Transport transport, Authentication authentication,
                                Protocol protocol, List<SetupInput> missingInputs) {}

    public record SetupInput(InputKind kind, String name) {}

    public enum OptionSource { REMOTE, PACKAGE }

    public enum Transport { STREAMABLE_HTTP, SSE, STDIO, UNKNOWN }

    public enum Authentication {
        UNKNOWN, DECLARED_REQUIRED, REQUIRED, OAUTH_ADVERTISED, INITIALIZED_WITHOUT_AUTH, DECLARATION_MISMATCH
    }

    public enum Protocol { UNKNOWN, UNVERIFIED, UNSUPPORTED_BY_CHECK, INITIALIZE_SUCCEEDED, MISMATCH }

    public enum InputKind {
        HEADER, TEMPLATE_VARIABLE, ENVIRONMENT_VARIABLE, RUNTIME_ARGUMENT, PACKAGE_ARGUMENT,
        AUTHORIZATION, OAUTH_AUTHORIZATION
    }
}
