package org.ndexbio.rest.helpers;

import java.io.IOException;
import org.ndexbio.rest.models.BaseTerm;
import org.ndexbio.rest.models.FunctionTerm;
import org.ndexbio.rest.models.Term;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TermDeserializer extends JsonDeserializer<Term>
{
    public TermDeserializer()
    {
        super();
    }
    
    
    
    @Override
    public Term deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException, JsonProcessingException
    {
        final ObjectMapper jsonMapper = new ObjectMapper();
        final JsonNode serializedTerm = jsonMapper.readTree(jsonParser);
        final JsonNode termType = serializedTerm.get("termType");
        
        if (termType != null)
        {
            if (termType.asText().equals("Base"))
                return populateBaseTerm(serializedTerm);
            else if (termType.asText().equals("Function"))
                return populateFunctionTerm(serializedTerm);
        }
        else
        {
            final JsonNode nameProperty = serializedTerm.get("name");
            if (nameProperty != null)
                return populateBaseTerm(serializedTerm);
            
            final JsonNode functionProperty = serializedTerm.get("termFunction");
            if (functionProperty != null)
                return populateFunctionTerm(serializedTerm);
        }
        
        throw context.mappingException("Unsupported term type.");
    }
    
    
    
    private BaseTerm populateBaseTerm(JsonNode serializedTerm)
    {
        BaseTerm baseTerm = new BaseTerm();
        baseTerm.setName(serializedTerm.get("name").asText());
        
        if (serializedTerm.get("namespace") != null)
            baseTerm.setNamespace(serializedTerm.get("namespace").asText());
        
        return baseTerm;
    }
    
    private FunctionTerm populateFunctionTerm(JsonNode serializedTerm)
    {
        FunctionTerm functionTerm = new FunctionTerm();
        functionTerm.setTermFunction(serializedTerm.get("termFunction").asText());

        //TODO: Need to deserialize parameters, don't know what they look like yet
        
        return functionTerm;
    }
}
