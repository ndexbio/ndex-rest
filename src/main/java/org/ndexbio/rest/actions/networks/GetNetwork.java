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
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.helpers.NetworkHelper;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.Map;

/******************************************************************************
* HTTP GET /networks
******************************************************************************/
public class GetNetwork extends NdexAction<GetNetwork.GetNetworkContext>
{
    public static final class GetNetworkContext implements NdexAction.Context
    {
        private String networkId;
        private JsonNode network;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "GET|networks/*" };
    }


    @Override
    protected void action(GetNetworkContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        ORID networkRid = RidConverter.convertToRid(requestContext.networkId);
        final XNetwork network = orientDbGraph.getVertex(networkRid, XNetwork.class);
        if (network == null)
            return;

        final ObjectNode serializedNetwork = OBJECT_MAPPER.createObjectNode();

        final Map<String, String> propertiesMap = network.getProperties();
        serializedNetwork.put("Title", propertiesMap.get("title"));

        final ObjectNode namespaces = OBJECT_MAPPER.createObjectNode();
        NetworkHelper.serializeNamespaces(network, namespaces);
        serializedNetwork.put("Namespaces", namespaces);

        final ObjectNode terms = OBJECT_MAPPER.createObjectNode();
        NetworkHelper.serializeTerms(network.getTerms(), terms);
        serializedNetwork.put("Terms", terms);

        final ObjectNode nodes = OBJECT_MAPPER.createObjectNode();
        NetworkHelper.serializeNodes(network.getNodes(), nodes);
        serializedNetwork.put("Nodes", nodes);

        final ArrayNode edges = OBJECT_MAPPER.createArrayNode();
        NetworkHelper.serializeEdges(network, edges);
        serializedNetwork.put("Edges", edges);

        requestContext.network = serializedNetwork;
    }

    @Override
    protected String getDescription()
    {
        return "Gets a network by ID.";
    }

    @Override
    protected GetNetworkContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final String networkId = httpRequest.parameters.get("networkId");
        final GetNetworkContext requestContext = new GetNetworkContext();
        requestContext.networkId = networkId;

        return requestContext;
    }

    @Override
    protected Object serializeResult(GetNetworkContext requestContext)
    {
        return requestContext.network;
    }
}
