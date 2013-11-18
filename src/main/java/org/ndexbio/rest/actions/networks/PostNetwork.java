package org.ndexbio.rest.actions.networks;

import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/******************************************************************************
* HTTP POST /networks
******************************************************************************/
public class PostNetwork extends NdexAction<PostNetwork.PostNetworkContext>
{
    public class PostNetworkContext implements NdexAction.Context
    {
        private String networkId;
        private JsonNode metadata;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    
    @Override
    public String[] getNames()
    {
        return new String[] { "POST|networks/*" };
    }

    
    
    @Override
    protected void action(PostNetworkContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final XNetwork network = orientDbGraph.getVertex(RidConverter.convertToRid(requestContext.networkId), XNetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", requestContext.networkId);

        final Map<String, String> networkProperties = network.getProperties();
        if (networkProperties != null)
        {
            final Iterator<String> networkMetadata = requestContext.metadata.getFieldNames();
            while (networkMetadata.hasNext())
            {
                String propertyName = networkMetadata.next();
                networkProperties.put(propertyName, requestContext.metadata.get(propertyName).asText());
            }
    
            network.setProperties(networkProperties);
        }
        else
        {
            Map<String, String> propertiesMap = new HashMap<String, String>();
            JsonNode properties = requestContext.metadata;
            
            Iterator<String> propertiesIterator = properties.getFieldNames();
            while (propertiesIterator.hasNext())
            {
                String index = propertiesIterator.next();
                propertiesMap.put(index, properties.get(index).asText());
            }

            network.setProperties(propertiesMap);
        }
    }

    @Override
    protected String getDescription()
    {
        return "Update a network's metadata.";
    }

    @Override
    protected PostNetworkContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final PostNetworkContext requestContext = new PostNetworkContext();
        final JsonNode rootNode = OBJECT_MAPPER.readTree(httpRequest.content);
        requestContext.networkId = rootNode.get("networkId").asText();
        requestContext.metadata = rootNode.get("metadata");

        return requestContext;
    }

    @Override
    protected Object serializeResult(PostNetworkContext requestContext)
    {
        return requestContext.networkId;
    }
}
