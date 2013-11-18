package org.ndexbio.rest.actions.requests;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XGroup;
import org.ndexbio.rest.domain.XRequest;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.Date;

/******************************************************************************
* HTTP PUT /requests 
******************************************************************************/
public class PutRequest extends NdexAction<PutRequest.PutRequestContext>
{
    public class PutRequestContext implements NdexAction.Context
    {
        private String fromId;
        private String toId;
        private String aboutId;
        private String message;
        private String requestType;
        private XRequest request;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "PUT|requests/*" };
    }

    

    @Override
    protected void action(PutRequestContext context, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID fromRid = RidConverter.convertToRid(context.fromId);
        final ORID toRid = RidConverter.convertToRid(context.toId);
        final ORID aboutRid = RidConverter.convertToRid(context.aboutId);

        final XUser fromAccount = orientDbGraph.getVertex(fromRid, XUser.class);
        XUser requestUser = null;
        XGroup requestGroup = null;
        
        final XRequest request = orientDbGraph.addVertex("class:xRequest", XRequest.class);
        request.setMessage(context.message);
        request.setRequestType(context.requestType);
        request.setRequestTime(new Date());
        request.addFromAccount(fromAccount);

        switch (context.requestType)
        {
            case "REQUEST_TO_JOIN_GROUP":
                requestGroup = orientDbGraph.getVertex(toRid, XGroup.class);
                request.addToAccount(requestGroup);
                request.addAbout(requestGroup);
                break;
            case "INVITATION_TO_JOIN_GROUP":
                requestUser = orientDbGraph.getVertex(toRid, XUser.class);
                requestGroup = orientDbGraph.getVertex(aboutRid, XGroup.class);
                request.addToAccount(requestUser);
                request.addAbout(requestGroup);
                break;
            default:
                throw new IllegalArgumentException("Unknown Request type: " + context.requestType + ".");
        }

        context.request = request;
    }
    
    @Override
    protected String getDescription()
    {
        return "Creates a request.";
    }

    protected PutRequestContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final PutRequestContext requestContext = new PutRequestContext();

        JsonNode rootNode = OBJECT_MAPPER.readTree(httpRequest.content);
        requestContext.fromId = rootNode.get("FromId").asText();
        requestContext.toId = rootNode.get("ToId").asText();
        requestContext.aboutId = rootNode.get("AboutId").asText();
        requestContext.requestType = rootNode.get("RequestType").asText();
        requestContext.message = rootNode.get("Message").asText();
        
        return requestContext;
    }

    @Override
    protected Object serializeResult(PutRequestContext requestContext)
    {
        final ObjectNode newRequest = OBJECT_MAPPER.createObjectNode();
        newRequest.put("Id", RidConverter.convertToJid((ORID)requestContext.request.asVertex().getId()));
        newRequest.put("Message", requestContext.request.getMessage());
        newRequest.put("RequestType", requestContext.request.getRequestType());

        return newRequest;
    }
}
