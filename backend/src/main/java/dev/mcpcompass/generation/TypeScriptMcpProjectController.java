package dev.mcpcompass.generation;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/generation/projects/typescript")
class TypeScriptMcpProjectController {
    private final TypeScriptMcpProjectGenerator generator;

    TypeScriptMcpProjectController(TypeScriptMcpProjectGenerator generator) {
        this.generator = generator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    GeneratedTypeScriptProject generate(@RequestBody McpToolContract contract) {
        return generator.generate(contract);
    }
}
