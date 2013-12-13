package org.ndexbio.rest.services;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

public class RequestServiceTest extends NdexServiceTest
{
    private static final Logger _logger = LoggerFactory.getLogger(RequestService.class);
    
    

    public RequestServiceTest()
    {
        super();
    }
    

    public Request createRequest(final Request newRequest) throws IllegalArgumentException, NdexException
    {
        if (newRequest == null)
            throw new IllegalArgumentException("The request to create is empty.");
        
        final ORID fromRid = RidConverter.convertToRid(newRequest.getFromId());
        final ORID toRid = RidConverter.convertToRid(newRequest.getToId());
        
        if (fromRid.equals(toRid))
            throw new IllegalArgumentException("Nice try, but you cannot make a request to yourself. You may want to consider schizophrenia counseling.");
        
        try
        {
            setupDatabase();
            
            final List<ODocument> existingRequests = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Request where out_fromUser = " + fromRid.toString() + " and (out_toNetwork = " + toRid.toString() + " or out_toGroup = " + toRid.toString() + ")"));
            if (existingRequests == null || existingRequests.isEmpty())
                throw new NdexException("Unable to get request count.");
            else if ((long)existingRequests.get(0).field("count") > 0)
                throw new NdexException("You have already made that request and cannot make another.");
            
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
            _logger.error("Failed to create a request.", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to create your request.");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    private void createGroupInvitationRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws ObjectNotFoundException, NdexException
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
    

    private void createJoinGroupRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws ObjectNotFoundException, NdexException
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
    

    private void createNetworkAccessRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws ObjectNotFoundException, NdexException
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
