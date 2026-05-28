package org.ndexbio.rest.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestToolsService {

    private ToolsService toolsService;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
    }

    @Test
    void unauthorizedResult_isError() {
        McpSchema.CallToolResult result = toolsService.unauthorizedResult();
        assertTrue(result.isError(), "unauthorizedResult should have isError=true");
    }

    @Test
    void unauthorizedResult_hasUnauthorizedText() {
        McpSchema.CallToolResult result = toolsService.unauthorizedResult();
        assertFalse(result.content().isEmpty(), "unauthorizedResult should have content");
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertEquals("401 Unauthorized", text.text());
    }

    @Test
    void unauthorizedResult_returnsNewInstanceEachCall() {
        McpSchema.CallToolResult r1 = toolsService.unauthorizedResult();
        McpSchema.CallToolResult r2 = toolsService.unauthorizedResult();
        assertNotSame(r1, r2, "each call should return a new instance");
    }
}
