package dev.mcpcompass.acceptance;

import com.sun.net.httpserver.HttpServer;
import dev.mcpcompass.github.GithubEnrichmentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class RegistrySearchAcceptanceTest {
    private static final String FIXTURE = "/fixtures/acceptance/registry-active-page.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicInteger REGISTRY_REQUESTS = new AtomicInteger();
    private static final AtomicReference<URI> REGISTRY_REQUEST_URI = new AtomicReference<>();

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer DATABASE =
            new PostgreSQLContainer("pgvector/pgvector:0.8.6-pg18-trixie");

    private static HttpServer registryServer;

    @LocalServerPort
    private int port;

    private final GithubEnrichmentService githubEnrichmentService;

    @Autowired
    RegistrySearchAcceptanceTest(GithubEnrichmentService githubEnrichmentService) {
        this.githubEnrichmentService = githubEnrichmentService;
    }

    @DynamicPropertySource
    static void registryProperties(DynamicPropertyRegistry properties) {
        startRegistryServer();
        properties.add(
                "app.registry.base-url",
                () -> "http://127.0.0.1:" + registryServer.getAddress().getPort()
        );
        properties.add("app.registry.page-size", () -> 25);
    }

    @AfterAll
    static void stopRegistryServer() {
        if (registryServer != null) {
            registryServer.stop(0);
        }
    }

    @Test
    void ingestsARegistryPageAndSearchesThePersistedServerOverHttp() throws Exception {
        assertThat(githubEnrichmentService).isNotNull();
        JsonNode syncResponse = post("/api/v1/dev/registry/sync?maxPages=1", null);

        assertThat(syncResponse.path("pages").intValue()).isEqualTo(1);
        assertThat(syncResponse.path("servers").intValue()).isEqualTo(3);
        assertThat(syncResponse.path("nextCursor").isNull()).isTrue();
        assertThat(REGISTRY_REQUESTS).hasValue(1);
        assertThat(REGISTRY_REQUEST_URI.get().getPath()).isEqualTo("/v0.1/servers");
        assertThat(REGISTRY_REQUEST_URI.get().getRawQuery()).isEqualTo("limit=25");

        JsonNode searchResponse = post(
                "/api/v1/mcp/search",
                """
                        {"requirement":"Read GitHub issues and create pull requests"}
                        """
        );

        assertThat(searchResponse.path("keywords").toString()).contains("github", "issues", "pull", "requests");
        JsonNode match = searchResponse.path("matches").get(0);
        assertThat(match.path("registryName").stringValue())
                .isEqualTo("io.github.modelcontextprotocol/github");
        assertThat(match.path("title").stringValue()).isEqualTo("GitHub MCP Server");
        assertThat(match.path("version").stringValue()).isEqualTo("1.2.3");
        assertThat(match.path("status").stringValue()).isEqualTo("active");
        assertThat(match.path("score").doubleValue()).isGreaterThan(0.0);
        assertThat(match.path("reasons").size()).isGreaterThan(0);

        JsonNode postgres = post("/api/v1/mcp/search", """
                {"requirement":"Query PostgreSQL tables over TLS in read-only mode. Inserts, updates, and deletes must not be available."}
                """);
        JsonNode excluded = candidate(postgres.path("exclusions"), "io.example/postgres-report-reader");
        assertThat(excluded.path("reasons").toString()).contains("static tool schema signal: ARBITRARY_SQL");
        JsonNode evidence = excluded.path("forbiddenCapabilityEvidence").get(0);
        assertThat(evidence.path("status").stringValue()).isEqualTo("SCHEMA_INFERRED_RISK");
        assertThat(evidence.path("permissionEnforcement").stringValue()).isEqualTo("NOT_VERIFIED");
        JsonNode report = evidence.path("staticToolSchema");
        assertThat(report.path("revision").stringValue()).isEqualTo("2.0.0");
        JsonNode schema = report.path("schemas").get(0);
        assertThat(schema.path("source").stringValue()).isEqualTo("publisher-extension-metadata");
        assertThat(schema.path("sha256").stringValue()).matches("[0-9a-f]{64}");
        assertThat(schema.path("uncertainty").stringValue()).isEqualTo("INFERRED");

        JsonNode catalog = candidate(postgres.path("matches"), "io.example/postgres-report-catalog");
        JsonNode catalogEvidence = catalog.path("forbiddenCapabilityEvidence").get(0);
        assertThat(catalogEvidence.path("status").stringValue()).isEqualTo("UNKNOWN");
        assertThat(catalogEvidence.path("permissionEnforcement").stringValue()).isEqualTo("NOT_VERIFIED");
        assertThat(catalogEvidence.path("staticToolSchema").path("schemas").get(0).path("uncertainty").stringValue())
                .isEqualTo("NO_SIGNAL");
        assertThat(REGISTRY_REQUESTS).as("search must use persisted Registry data").hasValue(1);
    }

    private static JsonNode candidate(JsonNode candidates, String registryName) {
        for (JsonNode candidate : candidates) {
            if (registryName.equals(candidate.path("registryName").stringValue())) {
                return candidate;
            }
        }
        throw new AssertionError("Missing " + registryName + " in " + candidates);
    }

    private JsonNode post(String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (body == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static void startRegistryServer() {
        if (registryServer != null) {
            return;
        }
        try (InputStream fixture = Objects.requireNonNull(
                RegistrySearchAcceptanceTest.class.getResourceAsStream(FIXTURE),
                FIXTURE
        )) {
            byte[] response = fixture.readAllBytes();
            registryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            registryServer.createContext("/v0.1/servers", exchange -> {
                REGISTRY_REQUESTS.incrementAndGet();
                REGISTRY_REQUEST_URI.set(exchange.getRequestURI());
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            registryServer.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start acceptance Registry stub", exception);
        }
    }
}
