package dev.mcpcompass.remote;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SetupReadinessTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void showsOAuthGateOnOnlyTheCheckedEndpointAndExposesNamesOnly() {
        McpServerEntity server = server("""
                {"server":{"remotes":[
                  {"type":"streamable-http","url":"https://fixture.invalid/mcp",
                   "headers":[{"name":"X-Team","isRequired":true},
                              {"name":"X-Provided","isRequired":true,"value":"fixture-value"}]},
                  {"type":"streamable-http","url":"https://alternative.invalid/mcp"}
                ]}}
                """);
        server.recordRemoteConnection("AUTH_REQUIRED", NOW, RemoteAuthenticationChallenge.reason(401, Map.of(
                "Www-Authenticate", List.of("Bearer realm=\"fixture\", resource_metadata=\"https://id.invalid/metadata\"")
        )));
        SetupReadiness readiness = SetupReadiness.from(server);
        assertThat(readiness.options()).satisfiesExactly(checked -> {
            assertThat(checked.authentication()).isEqualTo(SetupReadiness.Authentication.OAUTH_ADVERTISED);
            assertThat(checked.protocol()).isEqualTo(SetupReadiness.Protocol.UNVERIFIED);
            assertThat(checked.missingInputs()).containsExactly(
                    new SetupReadiness.SetupInput(SetupReadiness.InputKind.HEADER, "X-Team"),
                    new SetupReadiness.SetupInput(SetupReadiness.InputKind.OAUTH_AUTHORIZATION, null));
        }, unchecked -> {
            assertThat(unchecked.authentication()).isEqualTo(SetupReadiness.Authentication.UNKNOWN);
            assertThat(unchecked.protocol()).isEqualTo(SetupReadiness.Protocol.UNVERIFIED);
        });
        assertThat(new ObjectMapper().writeValueAsString(readiness))
                .doesNotContain("fixture-value", "X-Provided", "fixture.invalid", "id.invalid");
    }

    @Test
    void showsWhenGatewayBehaviorDisagreesWithRegistrySetupMetadata() {
        McpServerEntity server = server("""
                {"remotes":[{"type":"streamable-http","url":"https://gateway.invalid/mcp","headers":[
                  {"name":"Authorization","isRequired":true,"value":"Bearer {gateway_token}",
                   "variables":{"gateway_token":{"isSecret":true}}}
                ]}]}
                """);
        server.recordRemoteConnection("REACHABLE", NOW, "MCP initialize succeeded");
        assertThat(SetupReadiness.from(server).options()).singleElement().satisfies(option -> {
            assertThat(option.authentication()).isEqualTo(SetupReadiness.Authentication.DECLARATION_MISMATCH);
            assertThat(option.protocol()).isEqualTo(SetupReadiness.Protocol.INITIALIZE_SUCCEEDED);
            assertThat(option.missingInputs()).containsExactly(
                    new SetupReadiness.SetupInput(SetupReadiness.InputKind.TEMPLATE_VARIABLE, "gateway_token"));
        });
        server.recordRemoteConnection("PROTOCOL_MISMATCH", NOW, "Initialize response was not JSON");
        assertThat(SetupReadiness.from(server).options()).singleElement().satisfies(option -> {
            assertThat(option.authentication()).isEqualTo(SetupReadiness.Authentication.DECLARED_REQUIRED);
            assertThat(option.protocol()).isEqualTo(SetupReadiness.Protocol.MISMATCH);
        });
    }

    @Test
    void distinguishesUnsupportedRemoteTransportAndUnverifiedLocalPackage() {
        McpServerEntity server = server("""
                {"remotes":[{"type":"sse","url":"https://fixture.invalid/{team}/sse"}],
                 "packages":[{"transport":{"type":"stdio"},"environmentVariables":[
                   {"name":"MCP_TOKEN","isRequired":true,"isSecret":true},
                   {"name":"MCP_OPTIONAL","isRequired":false},
                   {"name":"MCP_TIMEOUT","isRequired":true,"default":30}
                 ],"packageArguments":[{"type":"named","name":"--project","isRequired":true}]}]}
                """);
        assertThat(SetupReadiness.from(server).options()).satisfiesExactly(remote -> {
            assertThat(remote.transport()).isEqualTo(SetupReadiness.Transport.SSE);
            assertThat(remote.protocol()).isEqualTo(SetupReadiness.Protocol.UNSUPPORTED_BY_CHECK);
            assertThat(remote.missingInputs()).containsExactly(
                    new SetupReadiness.SetupInput(SetupReadiness.InputKind.TEMPLATE_VARIABLE, "team"));
        }, pkg -> {
            assertThat(pkg.authentication()).isEqualTo(SetupReadiness.Authentication.UNKNOWN);
            assertThat(pkg.protocol()).isEqualTo(SetupReadiness.Protocol.UNVERIFIED);
            assertThat(pkg.missingInputs()).containsExactly(
                    new SetupReadiness.SetupInput(SetupReadiness.InputKind.ENVIRONMENT_VARIABLE, "MCP_TOKEN"),
                    new SetupReadiness.SetupInput(SetupReadiness.InputKind.PACKAGE_ARGUMENT, "--project"));
        });
    }

    @Test
    void keepsOldGenericAuthGatesAndAbsentOrInvalidMetadataUncertain() {
        McpServerEntity server = server("""
                {"remotes":[{"type":"streamable-http","url":"https://fixture.invalid/mcp"}]}
                """);
        server.recordRemoteConnection("AUTH_REQUIRED", NOW, "HTTP 403 requires authorization");
        assertThat(SetupReadiness.from(server).options()).singleElement().satisfies(option -> {
            assertThat(option.authentication()).isEqualTo(SetupReadiness.Authentication.REQUIRED);
            assertThat(option.protocol()).isEqualTo(SetupReadiness.Protocol.UNVERIFIED);
        });
        assertThat(SetupReadiness.from(server("{}")).options()).isEmpty();
        assertThat(SetupReadiness.from(server("malformed")).options()).isEmpty();
        assertThat(SetupReadiness.from(server(null)).options()).isEmpty();
        assertThat(SetupReadiness.from(server("{\"remotes\":[{}]}")).options()).singleElement().satisfies(option -> {
            assertThat(option.authentication()).isEqualTo(SetupReadiness.Authentication.UNKNOWN);
            assertThat(option.protocol()).isEqualTo(SetupReadiness.Protocol.UNKNOWN);
        });
    }

    private static McpServerEntity server(String metadata) {
        McpServerEntity server = McpServerEntity.create("io.example/setup-fixture", NOW);
        server.updateFrom(new RegistryClient.RegistryServerPayload(
                "io.example/setup-fixture", "Setup fixture", null, "1.0.0", "active", metadata,
                true, null, 0, 1, List.of(), List.of()), NOW);
        return server;
    }
}
