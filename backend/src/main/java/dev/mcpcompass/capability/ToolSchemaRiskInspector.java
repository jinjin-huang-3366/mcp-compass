package dev.mcpcompass.capability;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;
import java.util.regex.Pattern;

import static dev.mcpcompass.capability.ToolSchemaRiskReport.Signal;
import static dev.mcpcompass.capability.ToolSchemaRiskReport.Uncertainty;

final class ToolSchemaRiskInspector {
    static final String VERSION = "1";
    static final int MAX_TOOLS = 16;
    static final int MAX_NODES = 256;
    private static final int MAX_LABEL_LENGTH = 128;
    private static final Pattern SQL_CONTEXT = Pattern.compile("\\b(sql|postgres(?:ql)?)\\b");
    private static final Set<String> SQL_FIELDS = Set.of("sql", "rawsql", "sqlquery", "sqlstatement");
    private static final Set<String> QUERY_FIELDS = Set.of("query", "rawquery", "statement", "querytext");
    private static final Set<String> COMMAND_FIELDS = Set.of(
            "cmd", "command", "commandline", "shellcommand", "script", "shellscript", "powershellscript"
    );
    private static final Set<String> APPROVAL_FIELDS = Set.of(
            "approvalthreshold", "confirmationthreshold", "requiredapprovals", "minapprovals",
            "minimumapprovals", "requireapproval", "skipapproval", "bypassapproval"
    );
    private static final List<String> BRANCHES = List.of("anyOf", "oneOf", "prefixItems");
    private static final List<String> UNINSPECTED = List.of(
            "$ref", "$dynamicRef", "$recursiveRef", "dependentSchemas", "patternProperties", "if", "then", "else"
    );

    private ToolSchemaRiskInspector() {
    }

    static ToolSchemaRiskReport.InspectedSchema inspect(
            String tool, String source, String schemaText, JsonNode schema
    ) {
        if (schema == null) {
            return new ToolSchemaRiskReport.InspectedSchema(
                    label(tool, "(unnamed tool)"), label(source, "registry-tool-metadata"),
                    null, Uncertainty.UNAVAILABLE, List.of()
            );
        }
        Scan scan = new Scan();
        boolean sqlTool = SQL_CONTEXT.matcher(label(tool, "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")).find();
        scan.visit(schema, "", sqlTool);
        Uncertainty uncertainty = scan.incomplete ? Uncertainty.INCOMPLETE
                : scan.signals.isEmpty() ? Uncertainty.NO_SIGNAL : Uncertainty.INFERRED;
        return new ToolSchemaRiskReport.InspectedSchema(
                label(tool, "(unnamed tool)"), label(source, "registry-tool-metadata"),
                sha256(schemaText), uncertainty, List.copyOf(scan.signals)
        );
    }

    static String label(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        int end = Math.min(value.length(), MAX_LABEL_LENGTH);
        if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        String clipped = value.substring(0, end).replaceAll("[\\p{Cntrl}]", "");
        return clipped.isBlank() ? fallback : clipped;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static final class Scan {
        private final EnumSet<Signal> signals = EnumSet.noneOf(Signal.class);
        private int visited;
        private boolean incomplete;

        private void visit(JsonNode schema, String field, boolean sqlTool) {
            if (schema.isMissingNode()) {
                return;
            }
            if (++visited > MAX_NODES) {
                incomplete = true;
                return;
            }
            if (!schema.isObject()) {
                return;
            }
            incomplete |= UNINSPECTED.stream().anyMatch(schema::has);
            incomplete |= schema.path("additionalProperties").isObject();
            if (schema.has("const") || schema.path("enum").isArray()) {
                return;
            }
            if (!field.isEmpty() && !schema.has("allOf")) {
                recordSignals(schema, field, sqlTool);
            }
            for (var property : schema.path("properties").properties()) {
                if (visited > MAX_NODES) {
                    return;
                }
                String name = label(property.getKey(), "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                visit(property.getValue(), name, sqlTool);
            }
            visit(schema.path("items"), field, sqlTool);
            for (JsonNode option : schema.path("allOf")) {
                if (visited > MAX_NODES) {
                    return;
                }
                // Nested properties remain visible, but this pass cannot combine scalar constraints.
                incomplete |= !field.isEmpty();
                visit(option, "", sqlTool);
            }
            for (String branch : BRANCHES) {
                for (JsonNode option : schema.path(branch)) {
                    if (visited > MAX_NODES) {
                        return;
                    }
                    visit(option, field, sqlTool);
                }
            }
        }

        private void recordSignals(JsonNode schema, String field, boolean sqlTool) {
            if (hasType(schema, "string")) {
                JsonNode annotation = schema.path("description");
                String description = label(annotation.isString() ? annotation.stringValue() : null, "")
                        .toLowerCase(Locale.ROOT);
                boolean sql = SQL_FIELDS.contains(field) || (QUERY_FIELDS.contains(field)
                        && (sqlTool || SQL_CONTEXT.matcher(description).find()));
                boolean command = COMMAND_FIELDS.contains(field);
                if ((sql || command) && schema.has("pattern")) {
                    incomplete = true;
                } else if (sql) {
                    signals.add(Signal.ARBITRARY_SQL);
                } else if (command) {
                    signals.add(Signal.ARBITRARY_COMMAND);
                }
            }
            if (APPROVAL_FIELDS.contains(field) && (hasType(schema, "integer") || hasType(schema, "number")
                    || hasType(schema, "boolean"))) {
                JsonNode minimum = schema.path("minimum");
                if (minimum.isNumber() && minimum.equals(schema.path("maximum"))) {
                    return;
                }
                signals.add(Signal.CALLER_CONTROLLED_APPROVAL);
            }
        }

        private static boolean hasType(JsonNode schema, String expected) {
            JsonNode type = schema.path("type");
            return (type.isString() && expected.equals(type.stringValue()))
                    || (type.isArray() && StreamSupport.stream(type.spliterator(), false)
                            .anyMatch(node -> node.isString() && expected.equals(node.stringValue())));
        }
    }
}
