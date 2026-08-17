package dev.mcpcompass.requirement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "app.requirement.llm", name = "enabled", havingValue = "true")
public class OpenAiRequirementClient {
    private static final String INSTRUCTIONS = """
            Extract the developer's MCP server requirement into the supplied schema.
            Use an empty string when domain or service is not identifiable.
            Express capabilities as concise lower-case dotted identifiers.
            Include forbidden capabilities only when the user explicitly prohibits them.
            Include only hard conditions in constraints; do not turn preferences into constraints.
            """;

    private static final Map<String, Object> STRUCTURED_REQUIREMENT_SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of(
                    "schemaVersion",
                    "domain",
                    "service",
                    "requiredCapabilities",
                    "forbiddenCapabilities",
                    "constraints"
            ),
            "properties", Map.of(
                    "schemaVersion", Map.of("type", "string", "enum", List.of("1.0")),
                    "domain", Map.of("type", "string"),
                    "service", Map.of("type", "string"),
                    "requiredCapabilities", stringArraySchema(),
                    "forbiddenCapabilities", stringArraySchema(),
                    "constraints", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("name", "operator", "value"),
                                    "properties", Map.of(
                                            "name", Map.of("type", "string"),
                                            "operator", Map.of(
                                                    "type", "string",
                                                    "enum", Arrays.stream(RequirementConstraint.Operator.values())
                                                            .map(Enum::name)
                                                            .toList()
                                            ),
                                            "value", Map.of("type", "string")
                                    )
                            )
                    )
            )
    );

    private final ObjectMapper objectMapper;
    private final RequirementLlmProperties properties;
    private final RestClient restClient;

    public OpenAiRequirementClient(ObjectMapper objectMapper, RequirementLlmProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = validate(properties);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    public StructuredRequirement analyze(String requirement) {
        Objects.requireNonNull(requirement, "requirement must not be null");
        try {
            String response = restClient.post()
                    .uri("/v1/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(requirement))
                    .retrieve()
                    .body(String.class);
            return parseResponse(response);
        } catch (OpenAiRequirementClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OpenAiRequirementClientException(
                    "Unable to analyze requirement with the configured LLM",
                    exception
            );
        }
    }

    private Map<String, Object> request(String requirement) {
        return Map.of(
                "model", properties.model(),
                "store", false,
                "instructions", INSTRUCTIONS,
                "input", requirement,
                "max_output_tokens", properties.maxOutputTokens(),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "mcp_compass_requirement",
                                "strict", true,
                                "schema", STRUCTURED_REQUIREMENT_SCHEMA
                        )
                )
        );
    }

    private StructuredRequirement parseResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new OpenAiRequirementClientException("LLM response was empty");
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            String outputText = findOutputText(root);
            JsonNode requirement = objectMapper.readTree(outputText);
            return new StructuredRequirement(
                    requiredText(requirement, "schemaVersion"),
                    requiredTextAllowEmpty(requirement, "domain"),
                    requiredTextAllowEmpty(requirement, "service"),
                    textList(requirement, "requiredCapabilities"),
                    textList(requirement, "forbiddenCapabilities"),
                    constraints(requirement)
            );
        } catch (OpenAiRequirementClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OpenAiRequirementClientException("Unable to parse structured LLM response", exception);
        }
    }

    private static String findOutputText(JsonNode root) {
        for (JsonNode output : root.path("output")) {
            if (!"message".equals(output.path("type").stringValue())) {
                continue;
            }
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").stringValue())
                        && content.path("text").isString()
                        && !content.path("text").stringValue().isBlank()) {
                    return content.path("text").stringValue();
                }
            }
        }
        throw new OpenAiRequirementClientException("LLM response did not contain output text");
    }

    private static String requiredText(JsonNode node, String field) {
        String value = requiredTextAllowEmpty(node, field);
        if (value.isBlank()) {
            throw new OpenAiRequirementClientException("Structured LLM field was blank: " + field);
        }
        return value;
    }

    private static String requiredTextAllowEmpty(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            throw new OpenAiRequirementClientException("Structured LLM field was not text: " + field);
        }
        return value.stringValue();
    }

    private static List<String> textList(JsonNode node, String field) {
        JsonNode values = node.path(field);
        if (!values.isArray()) {
            throw new OpenAiRequirementClientException("Structured LLM field was not an array: " + field);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isString()) {
                throw new OpenAiRequirementClientException(
                        "Structured LLM array contained a non-text value: " + field
                );
            }
            result.add(value.stringValue());
        }
        return List.copyOf(result);
    }

    private static List<RequirementConstraint> constraints(JsonNode requirement) {
        JsonNode values = requirement.path("constraints");
        if (!values.isArray()) {
            throw new OpenAiRequirementClientException("Structured LLM field was not an array: constraints");
        }
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

    private static RequirementLlmProperties validate(RequirementLlmProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        requireNonBlank(properties.baseUrl(), "baseUrl");
        requireNonBlank(properties.apiKey(), "apiKey");
        requireNonBlank(properties.model(), "model");
        requirePositive(properties.connectTimeout(), "connectTimeout");
        requirePositive(properties.readTimeout(), "readTimeout");
        if (properties.maxOutputTokens() <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be greater than zero");
        }
        return properties;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank when LLM analysis is enabled");
        }
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
    }

    private static Map<String, Object> stringArraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
    }

    public static class OpenAiRequirementClientException extends RuntimeException {
        public OpenAiRequirementClientException(String message) {
            super(message);
        }

        public OpenAiRequirementClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
