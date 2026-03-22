package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

public class McpSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private McpSchema() {}

    /** Serialize an object instance to a JSON string (used for INPUT_SCHEMA constants). */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Derive a JSON schema string from a Jackson-annotated class via victools (for OUTPUT_SCHEMA). */
    public static String toSchemaJson(Class<?> clazz) {
        return toSchemaJson(clazz, new ObjectMapper());
    }

    /**
     * Derive a JSON schema string using the provided ObjectMapper (which may have MixIns
     * registered for external/third-party classes that cannot be annotated directly).
     */
    public static String toSchemaJson(Class<?> clazz, ObjectMapper mapper) {
        SchemaGenerator generator = new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(mapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonModule(JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY))
                .build());
        return generator.generateSchema(clazz).toPrettyString();
    }

    // --- InputProperty ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InputProperty(
        @JsonProperty("type")        String        type,
        @JsonProperty("description") String        description,
        @JsonProperty("items")       InputProperty items,
        @JsonProperty("enum")        List<String>  allowedValues) {

        public InputProperty(String type, String description) {
            this(type, description, null, null);
        }
        public InputProperty(String type, String description, List<String> allowedValues) {
            this(type, description, null, allowedValues);
        }
    }

    // --- InputSchema ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonSerialize(using = McpSchema.InputSchemaSerializer.class)
    public static class InputSchema {

        private final String type;
        private final List<String> required;
        private final Map<String, InputProperty> properties;

        @JsonCreator
        public InputSchema(
            @JsonProperty("type")       String type,
            @JsonProperty("required")   List<String> required,
            @JsonProperty("properties") Map<String, InputProperty> properties) {
            this.type = type;
            this.required = required;
            this.properties = properties;
        }

        public String getType()                           { return type; }
        public List<String> getRequired()                 { return required; }
        public Map<String, InputProperty> getProperties() { return properties; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final String type = "object";
            private final List<String> required   = new ArrayList<>();
            private final Map<String, InputProperty> properties = new LinkedHashMap<>();

            public Builder required(String... keys) {
                Collections.addAll(required, keys);
                return this;
            }
            public Builder property(String key, InputProperty prop) {
                properties.put(key, prop);
                return this;
            }
            public InputSchema build() {
                return new InputSchema(type, List.copyOf(required), Map.copyOf(properties));
            }
        }
    }

    // --- InputSchemaSerializer ---

    public static class InputSchemaSerializer extends JsonSerializer<InputSchema> {
        @Override
        public void serialize(InputSchema schema, JsonGenerator gen, SerializerProvider s)
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", schema.getType());
            if (schema.getRequired() != null) {
                gen.writeArrayFieldStart("required");
                for (String r : schema.getRequired()) gen.writeString(r);
                gen.writeEndArray();
            }
            gen.writeObjectFieldStart("properties");
            for (Map.Entry<String, InputProperty> e : schema.getProperties().entrySet()) {
                gen.writeObjectField(e.getKey(), e.getValue());
            }
            gen.writeEndObject(); // properties
            gen.writeEndObject(); // root
        }
    }
}
