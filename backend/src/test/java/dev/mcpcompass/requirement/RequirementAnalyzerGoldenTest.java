package dev.mcpcompass.requirement;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementAnalyzerGoldenTest {
    private static final String FIXTURE = "/fixtures/requirements/structured-requirement-golden-v1.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void goldenCorpusCoversRepresentativeRequirementShapes() throws Exception {
        JsonNode corpus = loadCorpus();
        List<JsonNode> cases = elements(corpus.path("cases"));

        assertThat(corpus.path("schemaVersion").stringValue())
                .isEqualTo(StructuredRequirement.CURRENT_SCHEMA_VERSION);
        assertThat(cases).hasSizeGreaterThanOrEqualTo(20);
        assertThat(cases.stream().map(entry -> requiredText(entry, "id")))
                .doesNotHaveDuplicates();
        assertThat(cases.stream().map(entry -> requiredText(entry, "requirement")))
                .doesNotHaveDuplicates();

        List<StructuredRequirement> expectedRequirements = cases.stream()
                .map(entry -> structuredRequirement(entry.path("expected")))
                .toList();
        assertThat(expectedRequirements)
                .anyMatch(requirement -> requirement.service().isEmpty())
                .allMatch(requirement -> !requirement.requiredCapabilities().isEmpty());
        assertThat(expectedRequirements.stream()
                .flatMap(requirement -> requirement.forbiddenCapabilities().stream()))
                .isNotEmpty();
        assertThat(expectedRequirements.stream()
                .flatMap(requirement -> requirement.constraints().stream())
                .map(RequirementConstraint::operator)
                .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder(RequirementConstraint.Operator.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenCases")
    void producesGoldenStructuredRequirement(String id, JsonNode goldenCase) throws Exception {
        String requirement = requiredText(goldenCase, "requirement");
        StructuredRequirement expected = structuredRequirement(goldenCase.path("expected"));
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(expected, requestBody);

        OpenAiRequirementClient client = new OpenAiRequirementClient(
                OBJECT_MAPPER,
                properties("http://127.0.0.1:" + server.getAddress().getPort())
        );
        OpenAiRequirementAnalyzer analyzer = new OpenAiRequirementAnalyzer(
                new HeuristicRequirementAnalyzer(),
                client
        );

        RequirementAnalysis result = analyzer.analyze(requirement);

        assertThat(id).isNotBlank();
        assertThat(result.originalRequirement()).isEqualTo(requirement);
        assertThat(result.keywords()).isNotEmpty();
        assertThat(result.structuredRequirement()).isEqualTo(expected);
        JsonNode request = OBJECT_MAPPER.readTree(requestBody.get());
        assertThat(request.path("input").stringValue()).isEqualTo(requirement);
        assertThat(request.path("store").booleanValue()).isFalse();
    }

    static Stream<Arguments> goldenCases() throws Exception {
        return elements(loadCorpus().path("cases")).stream()
                .map(entry -> Arguments.of(requiredText(entry, "id"), entry));
    }

    private void startServer(
            StructuredRequirement expected,
            AtomicReference<String> requestBody
    ) throws IOException {
        String outputText = OBJECT_MAPPER.writeValueAsString(expected);
        String response = """
                {
                  "output": [{
                    "type": "message",
                    "content": [{
                      "type": "output_text",
                      "text": %s
                    }]
                  }]
                }
                """.formatted(OBJECT_MAPPER.writeValueAsString(outputText));
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
    }

    private static JsonNode loadCorpus() throws IOException {
        try (InputStream input = RequirementAnalyzerGoldenTest.class.getResourceAsStream(FIXTURE)) {
            if (input == null) {
                throw new IllegalStateException("Missing golden requirement fixture: " + FIXTURE);
            }
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static StructuredRequirement structuredRequirement(JsonNode expected) {
        return new StructuredRequirement(
                requiredText(expected, "schemaVersion"),
                requiredTextAllowEmpty(expected, "domain"),
                requiredTextAllowEmpty(expected, "service"),
                textList(expected, "requiredCapabilities"),
                textList(expected, "forbiddenCapabilities"),
                constraints(expected.path("constraints"))
        );
    }

    private static List<RequirementConstraint> constraints(JsonNode values) {
        List<RequirementConstraint> constraints = new ArrayList<>();
        for (JsonNode value : values) {
            constraints.add(new RequirementConstraint(
                    requiredText(value, "name"),
                    RequirementConstraint.Operator.valueOf(requiredText(value, "operator")),
                    requiredText(value, "value")
            ));
        }
        return List.copyOf(constraints);
    }

    private static List<String> textList(JsonNode node, String field) {
        return elements(node.path(field)).stream()
                .map(JsonNode::stringValue)
                .toList();
    }

    private static List<JsonNode> elements(JsonNode values) {
        return StreamSupport.stream(values.spliterator(), false).toList();
    }

    private static String requiredText(JsonNode node, String field) {
        String value = requiredTextAllowEmpty(node, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Golden fixture field was blank: " + field);
        }
        return value;
    }

    private static String requiredTextAllowEmpty(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            throw new IllegalArgumentException("Golden fixture field was not text: " + field);
        }
        return value.stringValue();
    }

    private static RequirementLlmProperties properties(String baseUrl) {
        return new RequirementLlmProperties(
                true,
                baseUrl,
                "golden-test-api-key",
                "golden-test-model",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                800
        );
    }
}
