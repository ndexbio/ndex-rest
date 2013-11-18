package org.ndexbio.rest.actions.networks;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.List;

/******************************************************************************
* HTTP POST /networks/find 
******************************************************************************/
public class FindNetworks extends NdexAction<FindNetworks.FindNetworksContext>
{
    public static final class FindNetworksContext implements NdexAction.Context
    {
        private String searchExpression;
        private int skip;
        private int top;
        private JsonNode networks;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    @Override
    public String[] getNames()
    {
        return new String[] { "POST|networks/find/*" };
    }

    

    @Override
    protected void action(FindNetworksContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        requestContext.searchExpression = requestContext.searchExpression.trim();
        int startIndex = requestContext.skip * requestContext.top;

        String whereClause = "";
        if (requestContext.searchExpression.length() > 0)
            whereClause = " where properties.title.toUpperCase() like '%"
                + requestContext.searchExpression
                + "%' OR properties.description.toUpperCase() like '%"
                + requestContext.searchExpression + "%'";

        final String sqlQuery = "select from xNetwork " + whereClause + " order by creation_date desc skip " + startIndex + " limit " + requestContext.top;
        List<ODocument> networks = orientDbGraph
            .getBaseGraph()
            .getRawGraph()
            .query(new OSQLSynchQuery<ODocument>(sqlQuery));
        
        final String countQuery = "select count(*) as total from xNetwork " + whereClause;
        List<ODocument> networkCount = orientDbGraph
            .getBaseGraph()
            .getRawGraph()
            .query(new OSQLSynchQuery<ODocument>(countQuery));

        ArrayNode networksFound = OBJECT_MAPPER.createArrayNode();
        for (ODocument document : networks)
        {
            ObjectNode network = OBJECT_MAPPER.createObjectNode();
            networksFound.add(network);

            XNetwork xNetwork = orientDbGraph.getVertex(document, XNetwork.class);
            network.put("Title", xNetwork.getProperties().get("title"));
            network.put("Id", RidConverter.convertToJid((ORID)xNetwork.asVertex().getId()));
            network.put("Nodes", xNetwork.getNodesCount());
            network.put("Edges", xNetwork.getEdgesCount());
        }
        
        ObjectNode serializedNetworks = OBJECT_MAPPER.createObjectNode();
        serializedNetworks.put("Networks", networksFound);
        serializedNetworks.put("Total", networkCount.get(0).field("total").toString());

        requestContext.networks = serializedNetworks;
    }

    @Override
    protected String getDescription()
    {
        return "Find networks using the given search criteria.";
    }

    @Override
    protected FindNetworksContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final JsonNode rootNode = OBJECT_MAPPER.readTree(httpRequest.content);

        FindNetworksContext requestContext = new FindNetworksContext();
        requestContext.top = rootNode.get("top").asInt(100);
        requestContext.skip = rootNode.get("skip").asInt(0);
        requestContext.searchExpression = rootNode.get("searchExpression").toString();

        return requestContext;
    }

    @Override
    protected Object serializeResult(FindNetworksContext requestContext)
    {
        return requestContext.networks;
    }
}
