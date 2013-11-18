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

/******************************************************************************
* HTTP GET /networks/edges 
******************************************************************************/
public class GetEdges extends NdexAction<GetEdges.GetNetworkEdgesContext>
{
    public static final class GetNetworkEdgesContext implements NdexAction.Context
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
        return new String[] { "GET|networks/edges/*" };
    }

    

    @Override
    protected void action(GetNetworkEdgesContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        ORID networkRid = RidConverter.convertToRid(requestContext.networkId);
        ObjectNode serializedEdges = OBJECT_MAPPER.createObjectNode();

        XNetwork network = orientDbGraph.getVertex(networkRid, XNetwork.class);
        if (network == null)
            throw new ObjectNotFoundException("Network", requestContext.networkId);

        serializedEdges.put("Title", network.getProperties().get("title"));
        serializedEdges.put("Total", network.getEdgesCount());

        ArrayNode pageOfEdges = OBJECT_MAPPER.createArrayNode();
        final Iterable<XEdge> networkEdges = network.getNdexEdges();
        final int startIndex = requestContext.skip * requestContext.top;
        Collection<XEdge> loadedEdges = new ArrayList<XEdge>(requestContext.top);
        int counter = 0;
        
        for (XEdge networkEdge : networkEdges)
        {
            if (counter >= startIndex)
            {
                final ObjectNode serializedEdge = OBJECT_MAPPER.createObjectNode();
                NetworkHelper.serializeEdge(networkEdge, serializedEdge);
                pageOfEdges.add(serializedEdge);
                
                loadedEdges.add(networkEdge);
            }
            
            counter++;

            if (counter >= startIndex + requestContext.top)
                break;
        }

        serializedEdges.put("Edges", pageOfEdges);

        ObjectNode serializedNodes = OBJECT_MAPPER.createObjectNode();
        Collection<XNode> loadedNodes = NetworkHelper.loadNodeDependencies(loadedEdges);
        NetworkHelper.serializeNodes(loadedNodes, serializedNodes);
        serializedEdges.put("Nodes", serializedNodes);

        ObjectNode serializedTerms = OBJECT_MAPPER.createObjectNode();
        Collection<XTerm> loadedTerms = NetworkHelper.loadTermDependencies(loadedNodes, loadedEdges);
        NetworkHelper.serializeTerms(loadedTerms, serializedTerms);
        serializedEdges.put("Terms", serializedTerms);

        requestContext.network = serializedEdges;
    }

    @Override
    protected String getDescription()
    {
        return "Gets a network's edges.";
    }

    @Override
    protected GetNetworkEdgesContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final String networkId = httpRequest.parameters.get("networkid");

        int top = 100;
        if (httpRequest.parameters.get("top") != null)
            top = Integer.parseInt(httpRequest.parameters.get("top"));

        int skip = 0;
        if (httpRequest.parameters.get("skip") != null)
            skip = Integer.parseInt(httpRequest.parameters.get("skip"));

        GetNetworkEdgesContext requestContext = new GetNetworkEdgesContext();
        requestContext.top = top;
        requestContext.skip = skip;
        requestContext.networkId = networkId;

        return requestContext;
    }

    @Override
    protected Object serializeResult(GetNetworkEdgesContext context)
    {
        return context.network;
    }
}
