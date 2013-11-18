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
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import java.io.IOException;

/******************************************************************************
* HTTP PUT /worksurface 
******************************************************************************/
public class PutWorkSurface extends NdexAction<PutWorkSurface.PutWorkSurfaceContext>
{
    public static final class PutWorkSurfaceContext implements NdexAction.Context
    {
        private String userId;
        private String networkId;
    }
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    
    
    @Override
    public String[] getNames()
    {
        return new String[] { "PUT|worksurface/*" };
    }

    

    @Override
    protected void action(PutWorkSurfaceContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID userRid = RidConverter.convertToRid(requestContext.userId);
        final ORID networkRid = RidConverter.convertToRid(requestContext.networkId);

        XUser user = orientDbGraph.getVertex(userRid, XUser.class);
        if (user == null)
            throw new ObjectNotFoundException("User", requestContext.userId);

        XNetwork networkToAdd = orientDbGraph.getVertex(networkRid, XNetwork.class);
        Iterable<XNetwork> workSurface = user.getWorkspace();
        
        if (workSurface != null)
        {
            for (XNetwork network : workSurface)
            {
                if (network.asVertex().getId().equals(networkRid))
                    throw new DuplicateObjectException("Network with RID: " + networkRid + " is already on the Work Surface.");
            }
        }

        user.addWorkspace(networkToAdd);
    }

    @Override
    protected String getDescription()
    {
        return "Adds a network to a user's Work Surface.";
    }

    @Override
    protected PutWorkSurfaceContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        JsonNode serializedWorkSurface = OBJECT_MAPPER.readTree(httpRequest.content);
        PutWorkSurfaceContext requestContext = new PutWorkSurfaceContext();
        requestContext.networkId = serializedWorkSurface.get("NetworkId").asText();
        requestContext.userId = serializedWorkSurface.get("UserId").asText();

        return requestContext;
    }

    @Override
    protected Object serializeResult(PutWorkSurfaceContext requestContext)
    {
        return null;
    }
}