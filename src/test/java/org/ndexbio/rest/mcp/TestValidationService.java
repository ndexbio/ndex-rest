package org.ndexbio.rest.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestValidationService {

    private ValidationService service;

    @BeforeEach
    void setUp() {
        service = new ValidationService();
    }

    // --- convertToolArgs ---

    @Test
    void convertToolArgs_detectsConditionalWrapper() {
        Map<String, Object> args = Map.of(
                "folderId", Map.of("waived", false, "parameter", "some-uuid"));
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);
        assertTrue(result.get("folderId").isConditional(),
                "Map with waived+parameter keys should be classified as conditional");
    }

    @Test
    void convertToolArgs_treatsPlainValueAsRequired() {
        Map<String, Object> args = Map.of("mode", "create");
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);
        McpSchema.ToolInputParam param = result.get("mode");
        assertFalse(param.isConditional());
        assertEquals("create", param.requiredParameter());
    }

    // --- validateConditionalParams ---

    @Test
    void validateConditionalParams_missingCannotWaive_returnsError() {
        Map<String, Object> args = Map.of("mode", "create"); // folderId absent
        CallToolResult result = service.validateConditionalParams(
                "mode", "create", args,
                List.of(new ValidationService.ConditionalParam(
                        "folderId", "the folder to update", false)));
        assertNotNull(result, "absent cannot-waive param should produce an error");
        assertTrue(result.isError());
        assertTrue(((TextContent) result.content().get(0)).text().contains("folderId"));
    }

    @Test
    void validateConditionalParams_missingWaiveable_returnsNull() {
        Map<String, Object> args = Map.of("mode", "update"); // name absent but waiveable
        CallToolResult result = service.validateConditionalParams(
                "mode", "update", args,
                List.of(new ValidationService.ConditionalParam(
                        "name", "new folder name", true)));
        assertNull(result, "absent waiveable param should be treated as implicitly omitted");
    }

    @Test
    void validateConditionalParams_waivedTrueOnCannotWaive_returnsError() {
        Map<String, Object> waivedWrapper = new HashMap<>();
        waivedWrapper.put("waived", true);
        waivedWrapper.put("parameter", null);
        Map<String, Object> args = new HashMap<>();
        args.put("mode", "delete");
        args.put("folderId", waivedWrapper);
        CallToolResult result = service.validateConditionalParams(
                "mode", "delete", args,
                List.of(new ValidationService.ConditionalParam(
                        "folderId", "the folder to delete", false)));
        assertNotNull(result, "waived=true on cannot-waive param should produce an error");
        assertTrue(result.isError());
        assertTrue(((TextContent) result.content().get(0)).text().contains("folderId"));
    }

    @Test
    void validateConditionalParams_validWrapper_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("mode", "get");
        args.put("folderId", Map.of("waived", false, "parameter", "f93f402c-86d4-11e7-a10d-0ac135e8bacf"));
        CallToolResult result = service.validateConditionalParams(
                "mode", "get", args,
                List.of(new ValidationService.ConditionalParam(
                        "folderId", "the folder to retrieve", false)));
        assertNull(result, "valid {waived:false, parameter:value} wrapper should pass validation");
    }

    // --- unwrapToolInputValue ---

    @Test
    void unwrapToolInputValue_extractsFromWrapper() {
        Object wrapper = Map.of("waived", false, "parameter", "f93f402c-86d4-11e7-a10d-0ac135e8bacf");
        String result = service.unwrapToolInputValue(wrapper, String.class);
        assertEquals("f93f402c-86d4-11e7-a10d-0ac135e8bacf", result);
    }

    @Test
    void unwrapToolInputValue_returnsNullWhenWaived() {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("waived", true);
        wrapper.put("parameter", null);
        String result = service.unwrapToolInputValue(wrapper, String.class);
        assertNull(result, "waived=true should return null");
    }
}
