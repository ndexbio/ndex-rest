package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.IRequest;

public class Request extends NdexObject
{
    private String _fromName;
    private String _toName;
    private String _message;
    private String _requestType;

    

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Request()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param request The Request with source data.
    **************************************************************************/
    public Request(IRequest request)
    {
        super(request);
        
        _message = request.getMessage();
        this.setCreatedDate(request.getRequestTime());

        if (request instanceof IGroupInvitationRequest)
        {
            IGroupInvitationRequest groupRequest = ((IGroupInvitationRequest)request); 
            _requestType = "Group Invitation";
            _fromName = groupRequest.getFromGroup().getName();
            _toName = groupRequest.getToUser().getFirstName() + " " + groupRequest.getToUser().getLastName();
        }
        else if (request instanceof IJoinGroupRequest)
        {
            IJoinGroupRequest groupRequest = ((IJoinGroupRequest)request); 
            _requestType = "Network Access";
            _fromName = groupRequest.getFromUser().getFirstName() + " " + groupRequest.getFromUser().getLastName();
            _toName = groupRequest.getToGroup().getName();
        }
        else if (request instanceof INetworkAccessRequest)
        {
            INetworkAccessRequest networkRequest = ((INetworkAccessRequest)request); 
            _requestType = "Network Access";
            _fromName = networkRequest.getFromUser().getFirstName() + " " + networkRequest.getFromUser().getLastName();
            _toName = networkRequest.getToNetwork().getTitle();
        }
    }

    
    
    public String getFrom()
    {
        return _fromName;
    }

    public void setFromId(String fromName)
    {
        _fromName = fromName;
    }

    public String getMessage()
    {
        return _message;
    }

    public void setMessage(String message)
    {
        _message = message;
    }

    public String getRequestType()
    {
        return _requestType;
    }

    public void setRequestType(String requestType)
    {
        _requestType = requestType;
    }

    public String getTo()
    {
        return _toName;
    }

    public void setToId(String toName)
    {
        _toName = toName;
    }
}
