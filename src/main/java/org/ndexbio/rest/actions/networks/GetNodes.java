package org.ndexbio.rest.actions.networks;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XEdge;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XNode;
import org.ndexbio.rest.domain.XTerm;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.NetworkHelper;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/******************************************************************************
* HTTP GET /networks/nodes 
******************************************************************************/
public class GetNodes extends NdexAction<GetNodes.GetNetworkNodesContext>
{
    public static final class GetNetworkNodesContext implements NdexAction.Context
    {
        private String networkId;
        private int top;
        private int skip;
        private JsonNode network;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    
    @Override
    public String[] getNames()
    {
        return new String[] { "GET|networks/nodes/*" };
    }

    
    
    @Override
    protected void action(GetNetworkNodesContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        ORID networkRid = RidConverter.convertToRid(requestContext.networkId);
        ObjectNode serializedNodes = OBJECT_MAPPER.createObjectNode();

        XNetwork network = orientDbGraph.getVertex(networkRid, XNetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", requestContext.networkId);

        serializedNodes.put("Title", network.getProperties().get("title"));
        serializedNodes.put("Total", network.getNodesCount());

        ArrayNode pageOfNodes = OBJECT_MAPPER.createArrayNode();
        final Iterable<XNode> networkNodes = network.getNodes();
        final int startIndex = requestContext.skip * requestContext.top;
        Collection<XNode> loadedNodes = new ArrayList<XNode>(requestContext.top);
        int counter = 0;

        for (XNode networkNode : networkNodes)
        {
            if (counter >= startIndex)
            {
                final ObjectNode serializedNode = OBJECT_MAPPER.createObjectNode();
                NetworkHelper.serializeNode(networkNode, serializedNode);
                pageOfNodes.add(serializedNode);
                
                loadedNodes.add(networkNode);
            }
            
            counter++;

            if (counter >= startIndex + requestContext.top)
                break;
        }
        
        serializedNodes.put("Nodes", pageOfNodes);

        ObjectNode terms = OBJECT_MAPPER.createObjectNode();
        serializedNodes.put("Terms", terms);

        final Collection<XTerm> loadedTerms = NetworkHelper.loadTermDependencies(loadedNodes, Collections.<XEdge> emptyList());
        NetworkHelper.serializeTerms(loadedTerms, terms);

        requestContext.network = serializedNodes;
    }

    @Override
    protected String getDescription()
    {
        return "Gets a network's nodes.";
    }

    @Override
    protected GetNetworkNodesContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final String networkId = httpRequest.parameters.get("networkId");

        int top = 100;
        if (httpRequest.parameters.get("top") != null)
            top = Integer.parseInt(httpRequest.parameters.get("top"));

        int skip = 0;
        if (httpRequest.parameters.get("skip") != null)
            skip = Integer.parseInt(httpRequest.parameters.get("skip"));

        final GetNetworkNodesContext requestContext = new GetNetworkNodesContext();
        requestContext.top = top;
        requestContext.skip = skip;
        requestContext.networkId = networkId;

        return requestContext;
    }

    @Override
    protected Object serializeResult(GetNetworkNodesContext requestContext)
    {
        return requestContext.network;
    }
}
