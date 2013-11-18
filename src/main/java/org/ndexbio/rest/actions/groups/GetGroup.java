package org.ndexbio.rest.actions.groups;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XGroup;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.Collection;

/******************************************************************************
* HTTP GET /groups
******************************************************************************/
public class GetGroup extends NdexAction<GetGroup.GetGroupContext>
{
    public class GetGroupContext implements NdexAction.Context
    {
        private String groupId;
        private String groupName;
        private XGroup group;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "GET|groups/*" };
    }

    
    
    @Override
    protected void action(GetGroupContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        if (requestContext.groupId != null)
        {
            final ORID groupId = RidConverter.convertToRid(requestContext.groupId);
            final XGroup group = orientDbGraph.getVertex(groupId, XGroup.class);

            if (group == null)
                throw new ObjectNotFoundException("Group", requestContext.groupId);

            requestContext.group = group;
        }
        else if (requestContext.groupName != null)
        {
            final Collection<ODocument> matchingGroups = orientDbGraph.getBaseGraph()
                .command(new OCommandSQL("select from xGroup where groupname = ?"))
                .execute(requestContext.groupName);

            if (matchingGroups.size() < 1)
                throw new ObjectNotFoundException("Group", requestContext.groupName);
    
            requestContext.group = orientDbGraph.getVertex(matchingGroups.toArray()[0], XGroup.class);
        }
    }

    @Override
    protected String getDescription()
    {
        return "Gets a group by ID or name.";
    }

    @Override
    protected GetGroupContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final GetGroupContext requestContext = new GetGroupContext();
        requestContext.groupId = httpRequest.getParameter("groupId");
        requestContext.groupName = httpRequest.getParameter("groupName");

        return requestContext;
    }

    @Override
    protected Object serializeResult(GetGroupContext requestContext) throws Exception
    {
        final ObjectNode serializedGroup = OBJECT_MAPPER.createObjectNode();
        serializedGroup.put("Id",  RidConverter.convertToJid((ORID)requestContext.group.asVertex().getId()));
        serializedGroup.put("Name", requestContext.group.getGroupName());
        serializedGroup.put("Profile", serializeProfile(requestContext.group));
        serializedGroup.put("NetworksOwned", serializeNetworks(requestContext.group));

        return serializedGroup.toString();
    }



    /**************************************************************************
    * Serializes a group's profile.
    * 
    * @param group The group.
    **************************************************************************/
    private JsonNode serializeProfile(XGroup group)
    {
        final ObjectNode profile = OBJECT_MAPPER.createObjectNode();

        final String organizationName = group.getOrganizationName();
        if (organizationName != null)
            profile.put("OrganizationName", organizationName);

        final String website = group.getWebsite();
        if (website != null)
            profile.put("Website", website);

        final String foregroundImg = group.getForegroundImg();
        if (foregroundImg != null)
            profile.put("ForegroundImg", foregroundImg);

        final String backgroundImg = group.getBackgroundImg();
        if (backgroundImg != null)
            profile.put("BackgroundImg", backgroundImg);

        final String description = group.getDescription();
        if (description != null)
            profile.put("Description", description);

        return profile;
    }
    
    /**************************************************************************
    * Serializes the networks a group owns.
    * 
    * @param group The group.
    **************************************************************************/
    private JsonNode serializeNetworks(XGroup group)
    {
        final ArrayNode networks = OBJECT_MAPPER.createArrayNode();

        for (XNetwork network : group.getOwnedNetworks())
        {
            final ObjectNode networkNode = OBJECT_MAPPER.createObjectNode();
            networkNode.put("Id", RidConverter.convertToJid((ORID)network.asVertex().getId()));
            networkNode.put("Title", network.getProperties().get("title"));
            networkNode.put("Nodes", network.getNodesCount());
            networkNode.put("Edges", network.getEdgesCount());

            networks.add(networkNode);
        }
        
        return networks;
    }
}
