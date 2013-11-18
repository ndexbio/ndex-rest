package org.ndexbio.rest.actions.worksurface;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.server.network.protocol.http.OHttpRequest;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.ndexbio.rest.actions.NdexAction;
import org.ndexbio.rest.domain.XNetwork;
import org.ndexbio.rest.domain.XUser;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.helpers.UserHelper;
import java.io.IOException;

/******************************************************************************
* HTTP GET /worksurface 
******************************************************************************/
public class GetWorkSurface extends NdexAction<GetWorkSurface.GetWorkSurfaceContext>
{
    public static final class GetWorkSurfaceContext implements NdexAction.Context
    {
        private String userId;
        private JsonNode workSurface;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    

    @Override
    public String[] getNames()
    {
        return new String[] { "GET|worksurface/*" };
    }

    
    
    @Override
    protected void action(GetWorkSurfaceContext requestContext, FramedGraph<OrientBaseGraph> orientDbGraph)
    {
        final ORID userRid = RidConverter.convertToRid(requestContext.userId);
        final XUser user = orientDbGraph.getVertex(userRid, XUser.class);
        
        if (user == null)
            throw new ObjectNotFoundException("User", requestContext.userId);

        Iterable<XNetwork> workSurface = user.getWorkspace();
        final ArrayNode serializedNetworks = OBJECT_MAPPER.createArrayNode();
        UserHelper.serializeNetworks(workSurface.iterator(), serializedNetworks);

        requestContext.workSurface = serializedNetworks;
    }

    @Override
    protected String getDescription()
    {
        return "Gets a user's Work Surface.";
    }

    @Override
    protected GetWorkSurfaceContext parseRequest(OHttpRequest httpRequest) throws IOException
    {
        final GetWorkSurfaceContext requestContext = new GetWorkSurfaceContext();
        requestContext.userId = httpRequest.getParameter("UserId");

        return requestContext;
    }

    @Override
    protected Object serializeResult(GetWorkSurfaceContext requestContext)
    {
        return requestContext.workSurface;
    }
}