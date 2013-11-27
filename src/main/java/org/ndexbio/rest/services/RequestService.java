package org.ndexbio.rest.services;

import java.util.Date;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.IRequest;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Request;
import com.orientechnologies.orient.core.id.ORID;
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
    @Path("/{requestId}")
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
    @Path("/{requestId}")
    @Produces("application/json")
    public Request getRequest(@PathParam("requestId")final String requestJid) throws NdexException
    {
        final ORID requestId = RidConverter.convertToRid(requestJid);

        final IRequest request = _orientDbGraph.getVertex(requestId, IRequest.class);
        if (request == null)
            throw new ObjectNotFoundException("Request", requestJid);

        return new Request(request);
    }

    @POST
    @Produces("application/json")
    public void updateRequest(final Request updatedRequest) throws Exception
    {
        final ORID requestRid = RidConverter.convertToRid(updatedRequest.getId());

        final IRequest requestToUpdate = _orientDbGraph.getVertex(requestRid, IRequest.class);
        if (requestToUpdate == null)
            throw new ObjectNotFoundException("Request", updatedRequest.getId());

        try
        {
            requestToUpdate.setMessage(updatedRequest.getMessage());
            requestToUpdate.setRequestTime(updatedRequest.getCreatedDate());
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
    public Request createRequest(final Request requestToCreate) throws Exception
    {
        final ORID fromRid = RidConverter.convertToRid(requestToCreate.getFrom());
        final ORID toRid = RidConverter.convertToRid(requestToCreate.getTo());
        
        try
        {
            if (requestToCreate.getRequestType() == "Group Invitiation")
                createGroupInvitationRequest(fromRid, toRid, requestToCreate);
            else if (requestToCreate.getRequestType() == "Join Group")
                createJoinGroupRequest(fromRid, toRid, requestToCreate);
            else if (requestToCreate.getRequestType() == "Network Access")
                createNetworkAccessRequest(fromRid, toRid, requestToCreate);
            
            return requestToCreate;
        }
        catch (Exception e)
        {
            handleOrientDbException(e);
        }
        finally
        {
            closeOrientDbConnection();
        }
        
        return null;
    }
    
    
    
    /**************************************************************************
    * Creates a group invitation request. 
    * 
    * @param fromRid         The JID of the requesting group.
    * @param toRid           The JID of the invited user.
    * @param requestToCreate The request data. 
    **************************************************************************/
    private void createGroupInvitationRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws NdexException
    {
        final IGroup requestingGroup = _orientDbGraph.getVertex(fromRid, IGroup.class);
        if (requestingGroup == null)
            throw new ObjectNotFoundException("Group", fromRid);
        
        final IUser requestedUser = _orientDbGraph.getVertex(toRid, IUser.class);
        if (requestedUser == null)
            throw new ObjectNotFoundException("User", toRid);

        final IGroupInvitationRequest newRequest = _orientDbGraph.addVertex("class:groupInvite", IGroupInvitationRequest.class);
        newRequest.setMessage(requestToCreate.getMessage());
        newRequest.setRequestTime(new Date());
        newRequest.setFromGroup(requestingGroup);
        newRequest.setToUser(requestedUser);
        _orientDbGraph.getBaseGraph().commit();

        requestToCreate.setId(RidConverter.convertToJid((ORID)newRequest.asVertex().getId()));
    }
    
    /**************************************************************************
    * Creates a join group request. 
    * 
    * @param fromRid         The JID of the requesting user.
    * @param toRid           The JID of the requested group.
    * @param requestToCreate The request data. 
    **************************************************************************/
    private void createJoinGroupRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws NdexException
    {
        final IUser requestOwner = _orientDbGraph.getVertex(fromRid, IUser.class);
        if (requestOwner == null)
            throw new ObjectNotFoundException("User", fromRid);
        
        final IGroup requestedGroup = _orientDbGraph.getVertex(toRid, IGroup.class);
        if (requestedGroup == null)
            throw new ObjectNotFoundException("Group", toRid);

        final IJoinGroupRequest newRequest = _orientDbGraph.addVertex("class:joinGroup", IJoinGroupRequest.class);
        newRequest.setMessage(requestToCreate.getMessage());
        newRequest.setRequestTime(new Date());
        newRequest.setFromUser(requestOwner);
        newRequest.setToGroup(requestedGroup);
        _orientDbGraph.getBaseGraph().commit();

        requestToCreate.setId(RidConverter.convertToJid((ORID)newRequest.asVertex().getId()));
    }
    
    /**************************************************************************
    * Creates a network access request. 
    * 
    * @param fromRid         The JID of the requesting user.
    * @param toRid           The JID of the requested network.
    * @param requestToCreate The request data. 
    **************************************************************************/
    private void createNetworkAccessRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws NdexException
    {
        final IUser requestOwner = _orientDbGraph.getVertex(fromRid, IUser.class);
        if (requestOwner == null)
            throw new ObjectNotFoundException("User", fromRid);
        
        final INetwork requestedNetwork = _orientDbGraph.getVertex(toRid, INetwork.class);
        if (requestedNetwork == null)
            throw new ObjectNotFoundException("Network", toRid);

        final INetworkAccessRequest newRequest = _orientDbGraph.addVertex("class:networkAccess", INetworkAccessRequest.class);
        newRequest.setMessage(requestToCreate.getMessage());
        newRequest.setRequestTime(new Date());
        newRequest.setFromUser(requestOwner);
        newRequest.setToNetwork(requestedNetwork);
        _orientDbGraph.getBaseGraph().commit();

        requestToCreate.setId(RidConverter.convertToJid((ORID)newRequest.asVertex().getId()));
    }
}
