package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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
}
