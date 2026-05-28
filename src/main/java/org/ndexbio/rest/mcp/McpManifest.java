package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Standalone generator that produces McpManifest.md — a human-readable Markdown reference
 * describing every tool registered on the MCP server.
 *
 * <p>Usage: {@code McpManifest [outputDir]}
 *
 * <ul>
 *   <li>{@code outputDir} — directory where {@code McpManifest.md} is written; defaults to
 *       the current working directory if omitted.
 * </ul>
 *
 * <p>Invoked by exec-maven-plugin at the {@code prepare-package} phase. Output goes to
 * {@code target/classes/} so the file is bundled at the classpath root in the WAR and
 * served at runtime by {@link McpManifestServlet}.
 */
public final class McpManifest {

    public static void main(String[] args) throws Exception {
        Path outputDir = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.createDirectories(outputDir);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        List<McpSchema.Tool> tools = new McpToolRegistry().buildSpecs()
                .stream()
                .map(spec -> spec.tool())
                .toList();

        String toolsSection = renderTools(tools, mapper);
        String template = loadTemplate();
        String manifest = template.replace("{{TOOLS}}", toolsSection);

        Path outputFile = outputDir.resolve("McpManifest.md");
        Files.writeString(outputFile, manifest, StandardCharsets.UTF_8);
        System.out.println("McpManifest.md written to: " + outputFile.toAbsolutePath());
    }

    private static String renderTools(List<McpSchema.Tool> tools, ObjectMapper mapper)
            throws IOException {
        if (tools.isEmpty()) {
            return "*No tools registered.*\n";
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Tool tool : tools) {
            sb.append("### `").append(tool.name()).append("`\n\n");
            if (tool.description() != null && !tool.description().isBlank()) {
                sb.append("**Description:** ").append(tool.description()).append("\n\n");
            }
            if (tool.inputSchema() != null) {
                sb.append("**Input Schema:**\n\n```json\n")
                  .append(mapper.writeValueAsString(tool.inputSchema()))
                  .append("\n```\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    private static String loadTemplate() throws IOException {
        try (InputStream is = McpManifest.class.getResourceAsStream("/manifest_template.md")) {
            if (is == null) {
                throw new IllegalStateException(
                        "manifest_template.md not found on classpath at /manifest_template.md");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
