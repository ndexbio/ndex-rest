package org.ndexbio.rest.services;

import java.util.Collection;
import java.util.Date;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.IRequest;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Request;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.Vertex;

/*
 * class represents a service that supports  RESTful operations to perform
 * CRUD actions for request entities in the OrientDB  database
 * subclass of abstract class NdexService
 * FJC 19NOV2013
 */

@Path("/requests")
public class RequestService extends NdexService
{
    public RequestService()
    {
        super();
    }

    
    
    @DELETE
    @Path("/requests/{requestId}")
    @Produces("application/json")
    public void deleteRequest(@PathParam("requestId")final String requestJid) throws Exception
    {
        final ORID requestId = RidConverter.convertToRid(requestJid);

        final Vertex requestToDelete = _orientDbGraph.getVertex(requestId);
        if (requestToDelete == null)
            throw new ObjectNotFoundException("Request", requestJid);

        deleteVertex(requestToDelete);
    }

    @GET
    @Path("/requests/{requestId}")
    @Produces("application/json")
    public Request getRequest(@PathParam("requestId")final String requestJid) throws NdexException
    {
        final ORID requestId = RidConverter.convertToRid(requestJid);
        final IRequest request = _orientDbGraph.getVertex(requestId, IRequest.class);

        if (request == null)
        {
            final Collection<ODocument> matchingrequests = _orientDbGraph.getBaseGraph().command(new OCommandSQL("select from xrequest where requestname = ?")).execute(requestJid);

            if (matchingrequests.size() < 1)
                return null;
            else
                return new Request(_orientDbGraph.getVertex(matchingrequests.toArray()[0], IRequest.class));
        }
        else
            return new Request(request);
    }

    @POST
    @Produces("application/json")
    public void updateRequest(final Request updatedRequest) throws Exception
    {
        ORID requestRid = RidConverter.convertToRid(updatedRequest.getId());

        final IRequest requestToUpdate = _orientDbGraph.getVertex(requestRid, IRequest.class);
        if (requestToUpdate == null)
            throw new ObjectNotFoundException("Request", updatedRequest.getId());

        try
        {
            requestToUpdate.setMessage(updatedRequest.getMessage());
            ;
            requestToUpdate.setRequestTime(updatedRequest.getCreatedDate());
            requestToUpdate.setRequestType(updatedRequest.getRequestType());
            // TODO made remaining Request fields to XRequest
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
    }



    @PUT
    @Produces("application/json")
    public Request createRequest(final String ownerId, final Request newRequest) throws Exception
    {
        ORID userRid = RidConverter.convertToRid(ownerId);

        final IUser requestOwner = _orientDbGraph.getVertex(userRid, IUser.class);
        if (requestOwner == null)
            throw new ObjectNotFoundException("User", ownerId);


        try
        {
            final IRequest request = _orientDbGraph.addVertex("class:request", IRequest.class);
            request.setMessage(newRequest.getMessage());
            request.setRequestTime(new Date());
            request.setRequestType(newRequest.getRequestType());
            _orientDbGraph.getBaseGraph().commit();

            newRequest.setId(RidConverter.convertToJid((ORID) request.asVertex().getId()));
            return newRequest;
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
        return newRequest;
    }
}
