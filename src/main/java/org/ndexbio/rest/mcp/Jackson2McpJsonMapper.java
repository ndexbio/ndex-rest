package org.ndexbio.rest.mcp;

import java.io.IOException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

/**
 * Adapts the project's Jackson 2 ObjectMapper to the MCP SDK's McpJsonMapper interface,
 * avoiding a dependency on mcp-json-jackson3 (Jackson 3 / tools.jackson.*).
 */
public class Jackson2McpJsonMapper implements McpJsonMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public <T> T readValue(String content, Class<T> type) throws IOException {
        return MAPPER.readValue(content, type);
    }

    @Override
    public <T> T readValue(byte[] content, Class<T> type) throws IOException {
        return MAPPER.readValue(content, type);
    }

    @Override
    public <T> T readValue(String content, TypeRef<T> typeRef) throws IOException {
        return MAPPER.readValue(content, toJacksonTypeRef(typeRef));
    }

    @Override
    public <T> T readValue(byte[] content, TypeRef<T> typeRef) throws IOException {
        return MAPPER.readValue(content, toJacksonTypeRef(typeRef));
    }

    @Override
    public <T> T convertValue(Object fromValue, Class<T> type) {
        return MAPPER.convertValue(fromValue, type);
    }

    @Override
    public <T> T convertValue(Object fromValue, TypeRef<T> typeRef) {
        return MAPPER.convertValue(fromValue, toJacksonTypeRef(typeRef));
    }

    @Override
    public String writeValueAsString(Object value) throws IOException {
        return MAPPER.writeValueAsString(value);
    }

    @Override
    public byte[] writeValueAsBytes(Object value) throws IOException {
        return MAPPER.writeValueAsBytes(value);
    }

    private static <T> TypeReference<T> toJacksonTypeRef(TypeRef<T> typeRef) {
        return new TypeReference<T>() {
            @Override
            public java.lang.reflect.Type getType() {
                return typeRef.getType();
            }
        };
    }
}
