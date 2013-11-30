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
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Request;
import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.blueprints.Vertex;

@Path("/requests")
public class RequestService extends NdexService
{
    /**************************************************************************
    * Execute parent default constructor to initialize OrientDB.
    **************************************************************************/
    public RequestService()
    {
        super();
    }


    /**************************************************************************
    * Creates a request. 
    * 
    * @param newRequest The request to create.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Request createRequest(final Request newRequest) throws Exception
    {
        if (newRequest == null)
            throw new ValidationException("The request to create is empty.");
        
        final ORID fromRid = RidConverter.convertToRid(newRequest.getFromId());
        final ORID toRid = RidConverter.convertToRid(newRequest.getToId());
        
        try
        {
            if (newRequest.getRequestType().equals("Group Invitation"))
                createGroupInvitationRequest(fromRid, toRid, newRequest);
            else if (newRequest.getRequestType().equals("Join Group"))
                createJoinGroupRequest(fromRid, toRid, newRequest);
            else if (newRequest.getRequestType().equals("Network Access"))
                createNetworkAccessRequest(fromRid, toRid, newRequest);
            else
                throw new IllegalArgumentException("That request type isn't supported: " + newRequest.getRequestType() + ".");
            
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
        
        return null;
    }

    /**************************************************************************
    * Deletes a request.
    * 
    * @param requestId The ID of the request to delete.
    **************************************************************************/
    @DELETE
    @Path("/{requestId}")
    @Produces("application/json")
    public void deleteRequest(@PathParam("requestId")final String requestJid) throws Exception
    {
        if (requestJid == null || requestJid.isEmpty())
            throw new ValidationException("No request ID was specified.");
        
        final ORID requestId = RidConverter.convertToRid(requestJid);

        final Vertex requestToDelete = _orientDbGraph.getVertex(requestId);
        if (requestToDelete == null)
            throw new ObjectNotFoundException("Request", requestJid);

        //TODO: Need to remove orphaned vertices
        deleteVertex(requestToDelete);
    }

    /**************************************************************************
    * Gets a request by ID.
    * 
    * @param requestId The ID of the request.
    **************************************************************************/
    @GET
    @Path("/{requestId}")
    @Produces("application/json")
    public Request getRequest(@PathParam("requestId")final String requestJid) throws NdexException
    {
        if (requestJid == null || requestJid.isEmpty())
            throw new ValidationException("No request ID was specified.");
        
        final ORID requestId = RidConverter.convertToRid(requestJid);

        final IRequest request = _orientDbGraph.getVertex(requestId, IRequest.class);
        if (request == null)
            throw new ObjectNotFoundException("Request", requestJid);

        return new Request(request);
    }

    /**************************************************************************
    * Updates a request.
    * 
    * @param updatedRequest The updated request information.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateRequest(final Request updatedRequest) throws Exception
    {
        if (updatedRequest == null)
            throw new ValidationException("The updated request is empty.");
        
        final ORID requestRid = RidConverter.convertToRid(updatedRequest.getId());

        final IRequest requestToUpdate = _orientDbGraph.getVertex(requestRid, IRequest.class);
        if (requestToUpdate == null)
            throw new ObjectNotFoundException("Request", updatedRequest.getId());

        try
        {
            requestToUpdate.setResponder(updatedRequest.getResponder());
            requestToUpdate.setResponse(updatedRequest.getResponse());
            
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
        requestingGroup.addRequest(newRequest);
        requestedUser.addRequest(newRequest);
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
        requestOwner.addRequest(newRequest);
        requestedGroup.addRequest(newRequest);
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
        requestOwner.addRequest(newRequest);
        requestedNetwork.addRequest(newRequest);
        _orientDbGraph.getBaseGraph().commit();

        requestToCreate.setId(RidConverter.convertToJid((ORID)newRequest.asVertex().getId()));
    }
}
