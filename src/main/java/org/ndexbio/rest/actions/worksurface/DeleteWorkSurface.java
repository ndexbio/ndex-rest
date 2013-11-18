package org.ndexbio.rest.actions.worksurface;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;
import java.util.Iterator;

/******************************************************************************
* HTTP DELETE /worksurface 
******************************************************************************/
public class DeleteWorkSurface extends NdexAction<DeleteWorkSurface.DeleteWorkSurfaceContext>
{
    public final class DeleteWorkSurfaceContext implements NdexAction.Context
    {
        private String userId;
        private String networkId;
    }
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "DELETE|worksurface/*" };
    }

    
    
    @Override
    protected void action(DeleteWorkSurfaceContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID userRid = RidConverter.convertToRid(requestContext.userId);
        final ORID networkRid = RidConverter.convertToRid(requestContext.networkId);

        final XUser user = orientDbGraph.getVertex(userRid, XUser.class);
        if (user == null)
            throw new ObjectNotFoundException("User", requestContext.userId);

        Iterable<XNetwork> workSurface = user.getWorkspace();
        if (workSurface == null)
            return;

        Iterator<XNetwork> networkIterator = workSurface.iterator();
        while (networkIterator.hasNext())
        {
            XNetwork network = networkIterator.next();
            if (network.asVertex().getId().equals(networkRid))
            {
                networkIterator.remove();
                break;
            }
        }
    }

    @Override
    protected String getDescription()
    {
        return "Deletes a network from a user's Work Surface.";
    }

    @Override
    protected DeleteWorkSurfaceContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        DeleteWorkSurfaceContext requestContext = new DeleteWorkSurfaceContext();

        final JsonNode serializedNetwork = OBJECT_MAPPER.readTree(httpRequest.content);
        requestContext.networkId = serializedNetwork.get("NetworkId").asText();
        requestContext.userId = serializedNetwork.get("UserId").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(DeleteWorkSurfaceContext requestContext)
    {
        return null;
    }
}