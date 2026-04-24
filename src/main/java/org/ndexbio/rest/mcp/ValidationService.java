package org.ndexbio.rest.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Stateless service that validates ConditionalParameter wrapper presence and waive-intent,
 * and unwraps tool input values. Reusable across any MCP tool that declares conditional inputs.
 *
 * Ported from cytoscape-mcp ValidationService, adapted for the ndex-rest package.
 */
public class ValidationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Declares one conditional parameter for dynamic validation.
     *
     * @param name      parameter key in the args map
     * @param purpose   human-readable description of what this parameter controls (used in error messages)
     * @param waiveable true if the user may intentionally omit this parameter;
     *                  false if it is always required when its condition is active
     */
    public record ConditionalParam(String name, String purpose, boolean waiveable) {}

    // -- Arg conversion -------------------------------------------------------

    /**
     * Converts raw tool arguments into typed ToolInputParam entries.
     *
     * Detection rule: if a value is a Map that contains both "waived" and "parameter" keys,
     * it is classified as a ConditionalParameter; otherwise it is classified as a required parameter.
     */
    Map<String, McpSchema.ToolInputParam> convertToolArgs(Map<String, Object> args) {
        Map<String, McpSchema.ToolInputParam> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) val;
                if (map.containsKey("waived") && map.containsKey("parameter")) {
                    boolean waived = Boolean.TRUE.equals(map.get("waived"));
                    Object innerVal = map.get("parameter");
                    result.put(entry.getKey(),
                        new McpSchema.ToolInputParam(
                            new McpSchema.ConditionalParameter(waived, innerVal), null));
                    continue;
                }
            }
            result.put(entry.getKey(), new McpSchema.ToolInputParam(null, val));
        }
        return result;
    }

    // -- Validation -----------------------------------------------------------

    /**
     * Validates that all ConditionalParam entries have their ConditionalParameter wrapper present
     * in args, and that non-waiveable parameters have not been submitted with waived=true.
     *
     * @param dependentParamName  the context identifier in error messages (e.g. "mode")
     * @param dependentParamValue the value driving this conditional (e.g. "create")
     * @param args                the full arguments map from the tool request
     * @param conditionals        the list of conditional parameters to validate
     * @return an error CallToolResult if validation fails; null when all checks pass
     */
    public CallToolResult validateConditionalParams(
            String dependentParamName,
            String dependentParamValue,
            Map<String, Object> args,
            List<ConditionalParam> conditionals) {
        for (ConditionalParam cp : conditionals) {
            CallToolResult r = validatePresence(
                args, cp.name(), dependentParamName, dependentParamValue,
                cp.purpose(), !cp.waiveable());
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Checks that the given parameter key has a ConditionalParameter wrapper present in args.
     * If absent, returns a descriptive error. If present but waived=true on a cannot-waive param,
     * returns a descriptive error. Otherwise returns null (valid).
     */
    public CallToolResult validatePresence(
            Map<String, Object> args,
            String paramName,
            String dependentParamName,
            String dependentParamValue,
            String paramPurpose,
            boolean cannotWaive) {
        Object raw = args.get(paramName);
        if (raw == null) {
            if (!cannotWaive) {
                return null; // waiveable params may be absent; treat as implicitly omitted
            }
            return error(
                "Parameter '" + paramName + "' must be confirmed when " +
                dependentParamName + "=" + dependentParamValue +
                ": it controls " + paramPurpose +
                ". Provide a value (waived=false, parameter=<value>) or explicitly" +
                " confirm it should be omitted (waived=true). Refer to the '" +
                paramName + "' parameter description for complete details on how to use it.");
        }
        if (cannotWaive && raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> wrapper = (Map<String, Object>) raw;
            Object waivedVal = wrapper.get("waived");
            if (Boolean.TRUE.equals(waivedVal)) {
                return error(
                    "Parameter '" + paramName + "' cannot be intentionally omitted when " +
                    dependentParamName + "=" + dependentParamValue +
                    ": it controls " + paramPurpose +
                    " and is always required. Provide a value" +
                    " (waived=false, parameter=<value>). Refer to the '" +
                    paramName + "' parameter description for complete details.");
            }
        }
        return null;
    }

    // -- Value unwrapping -----------------------------------------------------

    /**
     * Extracts and converts a tool input value to the expected type.
     *
     * If the raw value is a ConditionalParameter wrapper (a Map with both "waived" and "parameter"
     * keys): returns null when waived=true; otherwise extracts the "parameter" value.
     * If not a conditional wrapper, the value itself is used directly (required parameter).
     *
     * Type conversion rules:
     * - String.class: trim, return null if empty
     * - Boolean.class: direct cast or null
     * - Integer.class: Number.intValue() or Integer.valueOf(value.toString())
     * - Other types: Jackson ObjectMapper.convertValue fallback
     */
    @SuppressWarnings("unchecked")
    public <T> T unwrapToolInputValue(Object rawArg, Class<T> expectedType) {
        if (rawArg == null) return null;

        Object value;
        if (rawArg instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) rawArg;
            if (map.containsKey("waived") && map.containsKey("parameter")) {
                if (Boolean.TRUE.equals(map.get("waived"))) return null;
                value = map.get("parameter");
            } else {
                value = rawArg;
            }
        } else {
            value = rawArg;
        }

        if (value == null) return null;

        if (expectedType == String.class) {
            if (!(value instanceof String)) return null;
            String s = ((String) value).trim();
            return s.isEmpty() ? null : (T) s;
        }

        if (expectedType == Boolean.class) {
            return value instanceof Boolean ? (T) value : null;
        }

        if (expectedType == Integer.class) {
            if (value instanceof Number) return (T) Integer.valueOf(((Number) value).intValue());
            try {
                return (T) Integer.valueOf(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        try {
            return MAPPER.convertValue(value, expectedType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -- Internal helpers -----------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
