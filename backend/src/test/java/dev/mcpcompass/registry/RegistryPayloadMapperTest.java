package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryPayloadMapperTest {
    private final RegistryPayloadMapper mapper = new RegistryPayloadMapper(new ObjectMapper());

    @Test
    void mapsDirectServerShape() {
        String body = """
                {
                  "servers": [{
                    "name": "io.example/direct",
                    "title": "Direct",
                    "description": "Example server",
                    "version": "1.2.3",
                    "status": "active"
                  }],
                  "metadata": {"nextCursor": "next-1"}
                }
                """;

        RegistryClient.RegistryPage page = mapper.map(body);

        assertThat(page.nextCursor()).isEqualTo("next-1");
        assertThat(page.servers()).singleElement().satisfies(server -> {
            assertThat(server.name()).isEqualTo("io.example/direct");
            assertThat(server.version()).isEqualTo("1.2.3");
            assertThat(server.status()).isEqualTo("active");
        });
    }

    @Test
    void mapsWrappedServerShapeAndWrapperStatus() {
        String body = """
                {
                  "servers": [{
                    "server": {
                      "name": "io.example/wrapped",
                      "title": "Wrapped",
                      "description": "Example server",
                      "version": "2.0.0"
                    },
                    "status": "deprecated"
                  }],
                  "metadata": {}
                }
                """;

        RegistryClient.RegistryPage page = mapper.map(body);

        assertThat(page.servers()).singleElement().satisfies(server -> {
            assertThat(server.name()).isEqualTo("io.example/wrapped");
            assertThat(server.status()).isEqualTo("deprecated");
        });
    }

    @Test
    void mapsRankingMetadataAndIgnoresIncompleteInstallOptions() {
        String body = """
                {
                  "servers": [{
                    "server": {
                      "name": "io.example/installable",
                      "version": "1.0.0",
                      "repository": {"url": "https://github.com/example/installable"},
                      "packages": [
                        {"registryType": "npm", "identifier": "@example/installable"},
                        {"registryType": "pypi"}
                      ],
                      "remotes": [
                        {"type": "streamable-http", "url": "https://example.com/mcp"},
                        {"url": "https://example.com/incomplete"}
                      ]
                    },
                    "_meta": {
                      "io.modelcontextprotocol.registry/official": {"status": "active"}
                    }
                  }]
                }
                """;

        RegistryClient.RegistryServerPayload server = mapper.map(body).servers().getFirst();

        assertThat(server.officialRegistryProvenance()).isTrue();
        assertThat(server.repositoryUrl()).isEqualTo("https://github.com/example/installable");
        assertThat(server.packageCount()).isEqualTo(1);
        assertThat(server.remoteCount()).isEqualTo(1);
    }

    @Test
    void mapsDeclaredCapabilitiesAndToolMetadataDefensively() {
        String body = """
                {
                  "servers": [{
                    "server": {
                      "name": "io.example/github",
                      "title": "GitHub",
                      "description": "Repository tools",
                      "version": "1.0.0",
                      "capabilities": [
                        "github.issue.read",
                        {"name": "github.repository.search", "description": "Search repositories"}
                      ],
                      "tools": [{
                        "name": "create_pull_request",
                        "description": "Create a pull request",
                        "inputSchema": {"type": "object"},
                        "capabilities": ["github.pull-request.create"]
                      }],
                      "_meta": {
                        "io.modelcontextprotocol.registry/publisher-provided": {
                          "capabilities": [{"canonicalName": "github.user.read"}],
                          "tools": [{
                            "name": "list_issues",
                            "input_schema": {"type": "object", "properties": {}}
                          }]
                        }
                      }
                    }
                  }],
                  "metadata": {}
                }
                """;

        RegistryClient.RegistryServerPayload server = mapper.map(body).servers().getFirst();

        assertThat(server.capabilities())
                .extracting(RegistryClient.RegistryCapabilityPayload::name)
                .containsExactly(
                        "github.issue.read",
                        "github.repository.search",
                        "github.user.read"
                );
        assertThat(server.tools()).extracting(RegistryClient.RegistryToolPayload::name)
                .containsExactly("create_pull_request", "list_issues");
        assertThat(server.tools().getFirst().inputSchema()).isEqualTo("{\"type\":\"object\"}");
        assertThat(server.tools().getFirst().capabilities())
                .extracting(RegistryClient.RegistryCapabilityPayload::name)
                .containsExactly("github.pull-request.create");
        assertThat(server.tools().get(1).capabilities()).isEqualTo(List.of());
    }
}
