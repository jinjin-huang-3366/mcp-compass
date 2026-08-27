package dev.mcpcompass.validationworker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ToolSecurityReport {
    private static final List<String> RISK_ORDER = List.of("READ_ONLY", "MUTATING", "DESTRUCTIVE");

    private ToolSecurityReport() {
    }

    static String create(
            String projectManifest,
            String inspectorOutput,
            ContainerSandboxPolicy policy,
            ObjectMapper objectMapper
    ) {
        JsonNode contract = contract(projectManifest, objectMapper);
        JsonNode inspectedTools = inspectedTools(inspectorOutput, objectMapper);
        Map<String, JsonNode> declared = declaredTools(contract);
        Set<String> observed = observedNames(inspectedTools);
        List<Map<String, Object>> tools = new ArrayList<>();
        List<String> findings = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : declared.entrySet()) {
            JsonNode tool = entry.getValue();
            String declaredRisk = supportedRisk(tool.path("risk").stringValue());
            String operationRisk = operationRisk(tool.path("sourceOperation").path("method").stringValue());
            boolean listed = observed.contains(entry.getKey());
            String effectiveRisk = listed && declaredRisk != null
                    ? highestRisk(declaredRisk, operationRisk)
                    : "DESTRUCTIVE";
            String basis = "APPROVED_CONTRACT";
            if (!listed) {
                findings.add("Approved tool was absent from Inspector tools/list: " + entry.getKey());
                basis = "CONSERVATIVE_DEFAULT";
            }
            if (declaredRisk == null) {
                findings.add("Tool had no supported approved risk and defaulted to DESTRUCTIVE: " + entry.getKey());
                basis = "CONSERVATIVE_DEFAULT";
            } else if (riskIndex(operationRisk) > riskIndex(declaredRisk)) {
                findings.add("Tool risk was upgraded from " + declaredRisk + " to " + operationRisk
                        + " for source operation " + tool.path("sourceOperation").path("method").stringValue()
                        + ": " + entry.getKey());
                basis = "CONSERVATIVE_METHOD_UPGRADE";
            }
            tools.add(toolEntry(
                    entry.getKey(), effectiveRisk, declaredRisk, basis, listed,
                    tool.path("sourceOperation"), tool.path("authenticationRequirements").size()
            ));
        }
        for (String name : observed) {
            if (!declared.containsKey(name)) {
                findings.add("Inspector exposed an undeclared tool defaulted to DESTRUCTIVE: " + name);
                tools.add(toolEntry(name, "DESTRUCTIVE", null, "CONSERVATIVE_DEFAULT", true, null, -1));
            }
        }
        tools.sort(Comparator.comparing(tool -> (String) tool.get("name")));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportVersion", "1.0");
        report.put("overallRisk", overallRisk(tools));
        report.put("tools", tools);
        report.put("findings", findings);
        report.put("sandbox", sandbox(policy));
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("validator", "mcp-inspector");
        protocol.put("validatorVersion", McpInspectorProtocolResult.INSPECTOR_VERSION);
        protocol.put("method", McpInspectorProtocolResult.METHOD);
        protocol.put("toolInvocationPerformed", false);
        report.put("protocol", protocol);
        report.put("limitations", List.of(
                "Risk is a deterministic classification of the approved contract and observed tool inventory, not a security certification.",
                "Validation lists tools but does not invoke them or verify upstream API behavior.",
                "A custom Docker network name is only a policy selector; destination filtering is operator-managed."
        ));
        return objectMapper.writeValueAsString(report);
    }

    private static JsonNode contract(String manifestJson, ObjectMapper objectMapper) {
        JsonNode files = objectMapper.readTree(manifestJson).path("files");
        if (!files.isArray()) {
            throw new IllegalArgumentException("Generated project manifest has no files array");
        }
        for (JsonNode file : files) {
            if ("contract.json".equals(file.path("path").stringValue())) {
                JsonNode contract = objectMapper.readTree(file.path("content").stringValue());
                if (contract != null && contract.path("tools").isArray()) {
                    return contract;
                }
            }
        }
        throw new IllegalArgumentException("Generated project manifest has no valid contract.json");
    }

    private static JsonNode inspectedTools(String output, ObjectMapper objectMapper) {
        JsonNode tools = objectMapper.readTree(output).path("result").path("tools");
        if (!tools.isArray()) {
            throw new IllegalArgumentException("MCP Inspector returned no tools/list result");
        }
        return tools;
    }

    private static Map<String, JsonNode> declaredTools(JsonNode contract) {
        Map<String, JsonNode> tools = new LinkedHashMap<>();
        for (JsonNode tool : contract.path("tools")) {
            String name = tool.path("name").stringValue();
            if (name == null || name.isBlank() || tools.put(name, tool) != null) {
                throw new IllegalArgumentException("Approved contract contains an invalid or duplicate tool name");
            }
        }
        return tools;
    }

    private static Set<String> observedNames(JsonNode tools) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode tool : tools) {
            String name = tool.path("name").stringValue();
            if (name == null || name.isBlank() || !names.add(name)) {
                throw new IllegalArgumentException("Inspector tools/list contains an invalid or duplicate tool name");
            }
        }
        return names;
    }

    private static Map<String, Object> toolEntry(
            String name,
            String effectiveRisk,
            String declaredRisk,
            String classificationBasis,
            boolean listed,
            JsonNode operation,
            int authenticationRequirementCount
    ) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("risk", effectiveRisk);
        if (declaredRisk != null) {
            tool.put("declaredRisk", declaredRisk);
        }
        tool.put("classificationBasis", classificationBasis);
        tool.put("listedByInspector", listed);
        if (operation != null && operation.isObject()) {
            tool.put("sourceOperation", operation.path("method").stringValue() + " " + operation.path("path").stringValue());
        }
        if (authenticationRequirementCount >= 0) {
            tool.put("authenticationRequired", authenticationRequirementCount > 0);
        }
        return tool;
    }

    private static Map<String, Object> sandbox(ContainerSandboxPolicy policy) {
        Map<String, Object> sandbox = new LinkedHashMap<>();
        sandbox.put("network", policy.network());
        sandbox.put("nonRootUser", policy.user());
        sandbox.put("cpuLimit", policy.cpuLimit());
        sandbox.put("memoryLimitMegabytes", policy.memoryLimitMegabytes());
        sandbox.put("processLimit", policy.processLimit());
        sandbox.put("wallTimeLimitSeconds", policy.wallTimeLimit().toSeconds());
        sandbox.put("readOnlyRootFilesystem", true);
        sandbox.put("readOnlyProjectMount", true);
        sandbox.put("capabilitiesDropped", "ALL");
        sandbox.put("noNewPrivileges", true);
        return sandbox;
    }

    private static String supportedRisk(String value) {
        return RISK_ORDER.contains(value) ? value : null;
    }

    private static String operationRisk(String method) {
        return switch (method == null ? "" : method) {
            case "GET", "HEAD", "OPTIONS" -> "READ_ONLY";
            case "POST", "PUT", "PATCH" -> "MUTATING";
            case "DELETE" -> "DESTRUCTIVE";
            default -> "DESTRUCTIVE";
        };
    }

    private static String highestRisk(String first, String second) {
        return riskIndex(first) >= riskIndex(second) ? first : second;
    }

    private static int riskIndex(String risk) {
        return RISK_ORDER.indexOf(risk);
    }

    private static String overallRisk(List<Map<String, Object>> tools) {
        return tools.stream()
                .map(tool -> (String) tool.get("risk"))
                .max(Comparator.comparingInt(ToolSecurityReport::riskIndex))
                .orElse("DESTRUCTIVE");
    }
}
