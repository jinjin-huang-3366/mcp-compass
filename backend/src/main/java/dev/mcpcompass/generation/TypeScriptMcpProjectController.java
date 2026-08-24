package dev.mcpcompass.generation;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/generation/projects/typescript")
class TypeScriptMcpProjectController {
    private final TypeScriptMcpProjectGenerator generator;
    private final TypeScriptMcpProjectArchive archive;

    TypeScriptMcpProjectController(TypeScriptMcpProjectGenerator generator, TypeScriptMcpProjectArchive archive) {
        this.generator = generator;
        this.archive = archive;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    GeneratedTypeScriptProject generate(@RequestBody McpToolContract contract) {
        return generator.generate(contract);
    }

    @PostMapping(path = "/export", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/zip")
    ResponseEntity<byte[]> export(@RequestBody McpToolContract contract) {
        TypeScriptMcpProjectArchive.Export exported = archive.export(generator.generate(contract));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + exported.fileName() + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(exported.content().length)
                .body(exported.content());
    }
}
