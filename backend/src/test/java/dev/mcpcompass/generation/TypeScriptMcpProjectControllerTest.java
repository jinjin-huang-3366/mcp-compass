package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TypeScriptMcpProjectControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsGeneratedProjectFiles() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new TypeScriptMcpProjectController(new TypeScriptMcpProjectGenerator(
                                objectMapper, new TypeScriptMcpRuntimePack()), new TypeScriptMcpProjectArchive()))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();

        mockMvc.perform(post("/api/v1/generation/projects/typescript")
                        .contentType("application/json")
                        .content(contract("APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("pet-store-mcp-server"))
                .andExpect(jsonPath("$.language").value("typescript"))
                .andExpect(jsonPath("$.files.length()").value(11))
                .andExpect(jsonPath("$.files[?(@.path == 'package-lock.json')]").exists())
                .andExpect(jsonPath("$.files[?(@.path == '.gitignore')]").exists())
                .andExpect(jsonPath("$.files[?(@.path == '.github/workflows/ci.yml')]").exists())
                .andExpect(jsonPath("$.files[?(@.path == 'src/api-client.test.ts')]").exists())
                .andExpect(jsonPath("$.files[?(@.path == 'src/index.ts')]").exists());
    }

    @Test
    void exportsGitHubReadyProjectAsZip() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new TypeScriptMcpProjectController(new TypeScriptMcpProjectGenerator(
                                objectMapper, new TypeScriptMcpRuntimePack()), new TypeScriptMcpProjectArchive()))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();

        byte[] archive = mockMvc.perform(post("/api/v1/generation/projects/typescript/export")
                        .contentType("application/json")
                        .content(contract("APPROVED")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"pet-store-mcp-server.zip\""))
                .andReturn().getResponse().getContentAsByteArray();

        Map<String, String> files = unzip(archive);
        assertThat(files).containsKeys(
                "pet-store-mcp-server/package.json",
                "pet-store-mcp-server/package-lock.json",
                "pet-store-mcp-server/.gitignore",
                "pet-store-mcp-server/.github/workflows/ci.yml",
                "pet-store-mcp-server/contract.json",
                "pet-store-mcp-server/src/index.ts");
        assertThat(files.get("pet-store-mcp-server/.github/workflows/ci.yml"))
                .contains("npm ci --ignore-scripts", "npm test");
    }

    @Test
    void rejectsUnapprovedContract() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new TypeScriptMcpProjectController(new TypeScriptMcpProjectGenerator(
                                objectMapper, new TypeScriptMcpRuntimePack()), new TypeScriptMcpProjectArchive()))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();

        mockMvc.perform(post("/api/v1/generation/projects/typescript")
                        .contentType("application/json")
                        .content(contract("PROPOSED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APPROVED_CONTRACT"));
    }

    private static Map<String, String> unzip(byte[] archive) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static String contract(String status) {
        return """
                {
                  "contractVersion":"1.0",
                  "status":"%s",
                  "source":{"type":"FILE","location":"petstore.yaml","openApiVersion":"3.1.0","title":"Pet Store","apiVersion":"1.0.0"},
                  "tools":[{
                    "name":"find_pets",
                    "description":"Find available pets",
                    "inputSchema":{"type":"object","properties":{"petId":{"type":"string"}},"required":["petId"]},
                    "outputSchema":{"type":"object"},
                    "sourceOperation":{"method":"GET","path":"/pets/{petId}","operationId":"getPet"},
                    "authenticationRequirements":[],
                    "risk":"READ_ONLY"
                  }]
                }
                """.formatted(status);
    }
}
