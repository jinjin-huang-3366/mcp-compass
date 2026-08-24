package dev.mcpcompass.generation;

/**
 * Builds a validated project snapshot without writing or executing generated files.
 */
public interface GeneratedProjectProvider {
    GeneratedTypeScriptProject generate(McpToolContract contract);
}
