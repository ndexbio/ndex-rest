package org.ndexbio.rest.actions.groups;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XGroup;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Pattern;

/******************************************************************************
* HTTP PUT /groups
******************************************************************************/
public class PutGroup extends NdexAction<PutGroup.PutGroupContext>
{
    public class PutGroupContext implements NdexAction.Context
    {
        private String userId;
        private String groupName;
        private XGroup group;
    }
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "PUT|groups/*" };
    }

    

    @Override
    protected void action(PutGroupContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        ORID userRid = RidConverter.convertToRid(requestContext.userId);
        
        final XUser groupOwner = orientDbGraph.getVertex(userRid, XUser.class);
        if (groupOwner == null)
            throw new ObjectNotFoundException("User", requestContext.userId);

        final Pattern groupNamePattern = Pattern.compile("^[A-Za-z0-9]{6,}$");
        if (!groupNamePattern.matcher(requestContext.groupName).matches())
            throw new ValidationException("Invalid group name: " + requestContext.groupName + ".");

        final XGroup newGroup = orientDbGraph.addVertex("class:xGroup", XGroup.class);
        newGroup.setGroupName(requestContext.groupName);
        newGroup.setCreationDate(new Date());

        groupOwner.addOwnedGroup(newGroup);

        requestContext.group = newGroup;
    }

    @Override
    protected String getDescription()
    {
        return "Creates a group.";
    }

    @Override
    protected PutGroupContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final PutGroupContext requestContext = new PutGroupContext();

        JsonNode newGroup = OBJECT_MAPPER.readTree(httpRequest.content);
        requestContext.userId = newGroup.get("userId").asText();
        requestContext.groupName = newGroup.get("groupName").asText();
        
        return requestContext;
    }

    @Override
    protected Object serializeResult(PutGroupContext requestContext)
    {
        final ObjectNode newGroup = OBJECT_MAPPER.createObjectNode();
        newGroup.put("Id", RidConverter.convertToJid((ORID)requestContext.group.asVertex().getId()));

        return newGroup;
    }
}
