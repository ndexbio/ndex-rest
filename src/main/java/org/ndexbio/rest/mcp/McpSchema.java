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

    // --- Conditional parameter support ---

    /**
     * Descriptor for a conditional parameter emitted as a {waived, parameter} wrapper object in
     * the JSON schema. The LLM must supply either {waived:true} (explicit omission confirmed by
     * the user) or {waived:false, parameter:<value>} (a value is provided).
     */
    public record ConditionalParamSpec(String paramType, String description) {}

    /** Runtime model for a deserialized {waived, parameter} wrapper supplied by the LLM. */
    public record ConditionalParameter(boolean waived, Object value) {}

    /**
     * Discriminated container for one tool input argument after type detection.
     * Exactly one field is non-null.
     */
    public record ToolInputParam(
            ConditionalParameter conditionalParameter,
            Object requiredParameter) {
        public boolean isConditional() { return conditionalParameter != null; }
    }

    /**
     * Standard description for the waived sub-field of every ConditionalParameter wrapper.
     * Instructs the LLM that setting this field to true requires explicit user confirmation.
     */
    public static final String WAIVED_FIELD_DESC =
        "Imperative: set to true only after direct user confirmation that this parameter " +
        "should be intentionally omitted. Set to false when providing a value in the parameter " +
        "field. Never assume or default — this requires explicit user confirmation or " +
        "unambiguous contextual evidence in the current interaction.";

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
        private final Map<String, ConditionalParamSpec> conditionalParamSpecs;

        @JsonCreator
        public InputSchema(
            @JsonProperty("type")       String type,
            @JsonProperty("required")   List<String> required,
            @JsonProperty("properties") Map<String, InputProperty> properties) {
            this(type, required, properties, Map.of());
        }

        private InputSchema(
            String type,
            List<String> required,
            Map<String, InputProperty> properties,
            Map<String, ConditionalParamSpec> conditionalParamSpecs) {
            this.type = type;
            this.required = required;
            this.properties = properties;
            this.conditionalParamSpecs = conditionalParamSpecs;
        }

        public String getType()                                            { return type; }
        public List<String> getRequired()                                  { return required; }
        public Map<String, InputProperty> getProperties()                  { return properties; }
        public Map<String, ConditionalParamSpec> getConditionalParamSpecs(){ return conditionalParamSpecs; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final String type = "object";
            private final List<String> required   = new ArrayList<>();
            private final Map<String, InputProperty> properties = new LinkedHashMap<>();
            private final Map<String, ConditionalParamSpec> conditionalParamSpecs = new LinkedHashMap<>();

            public Builder required(String... keys) {
                Collections.addAll(required, keys);
                return this;
            }
            public Builder property(String key, InputProperty prop) {
                properties.put(key, prop);
                return this;
            }
            /**
             * Declare a conditional scalar parameter wrapped as a {waived, parameter} object.
             * The LLM must supply the wrapper explicitly; the handler validates presence before
             * extracting the inner value via ValidationService.
             *
             * @param name      parameter key in the schema
             * @param paramType JSON primitive type for the parameter field: "string", "integer", or "boolean"
             * @param description LLM-facing description for this conditional parameter
             */
            public Builder conditionalParam(String name, String paramType, String description) {
                conditionalParamSpecs.put(name, new ConditionalParamSpec(paramType, description));
                return this;
            }
            public InputSchema build() {
                return new InputSchema(
                    type,
                    List.copyOf(required),
                    Map.copyOf(properties),
                    Collections.unmodifiableMap(new LinkedHashMap<>(conditionalParamSpecs)));
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
            // Regular InputProperty entries
            for (Map.Entry<String, InputProperty> e : schema.getProperties().entrySet()) {
                gen.writeObjectField(e.getKey(), e.getValue());
            }
            // ConditionalParameter<T> wrapper entries
            for (Map.Entry<String, ConditionalParamSpec> e :
                    schema.getConditionalParamSpecs().entrySet()) {
                ConditionalParamSpec spec = e.getValue();
                gen.writeObjectFieldStart(e.getKey());
                gen.writeStringField("type", "object");
                gen.writeStringField("description", spec.description());
                gen.writeObjectFieldStart("properties");
                // waived sub-field
                gen.writeObjectFieldStart("waived");
                gen.writeStringField("type", "boolean");
                gen.writeStringField("description", WAIVED_FIELD_DESC);
                gen.writeEndObject();
                // parameter sub-field
                gen.writeObjectFieldStart("parameter");
                gen.writeStringField("type", spec.paramType());
                gen.writeEndObject();
                gen.writeEndObject(); // end properties
                gen.writeEndObject(); // end conditional param wrapper
            }
            gen.writeEndObject(); // properties
            gen.writeEndObject(); // root
        }
    }
}
