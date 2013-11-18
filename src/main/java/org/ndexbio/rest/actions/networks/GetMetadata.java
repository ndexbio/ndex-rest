package org.ndexbio.rest.actions.networks;

import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.Map;

/******************************************************************************
* HTTP GET /networks/metadata 
******************************************************************************/
public class GetMetadata extends NdexAction<GetMetadata.GetMetadataContext>
{
    class GetMetadataContext implements NdexAction.Context
    {
        private String networkId;
        private Map<String, String> metadata;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "GET|networks/metadata/*" };
    }


    
    @Override
    protected void action(GetMetadataContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final XNetwork network = orientDbGraph.getVertex(RidConverter.convertToRid(requestContext.networkId), XNetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", requestContext.networkId);
        
        requestContext.metadata = network.getProperties();
    }

    @Override
    protected String getDescription()
    {
        return "Gets network metadata.";
    }

    @Override
    protected GetMetadataContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final GetMetadataContext requestContext = new GetMetadataContext();
        requestContext.networkId = httpRequest.getParameter("networkId");
        return requestContext;
    }

    @Override
    protected Object serializeResult(GetMetadataContext requestContext)
    {
        final ObjectNode networkProperties = OBJECT_MAPPER.createObjectNode();

        for (Map.Entry<String, String> e : requestContext.metadata.entrySet())
            networkProperties.put(e.getKey(), e.getValue());

        return networkProperties;
    }
}
