package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.IRequest;
import org.ndexbio.rest.helpers.RidConverter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.orientechnologies.orient.core.id.ORID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class Request extends NdexObject
{
    private String _fromId;
    private String _fromName;
    private String _toId;
    private String _toName;
    private String _message;
    private String _requestType;
    private String _response;
    private String _responder;

    

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
        _responder = request.getResponder();
        _response = request.getResponse();
        this.setCreatedDate(request.getRequestTime());

        if (request instanceof IGroupInvitationRequest)
        {
            IGroupInvitationRequest groupRequest = ((IGroupInvitationRequest)request); 
            _requestType = "Group Invitation";
            _fromId = RidConverter.convertToJid((ORID)groupRequest.getFromGroup().asVertex().getId());
            _fromName = groupRequest.getFromGroup().getName();
            _toId = RidConverter.convertToJid((ORID)groupRequest.getToUser().asVertex().getId());
            _toName = groupRequest.getToUser().getFirstName() + " " + groupRequest.getToUser().getLastName();
        }
        else if (request instanceof IJoinGroupRequest)
        {
            IJoinGroupRequest groupRequest = ((IJoinGroupRequest)request); 
            _requestType = "Join Group";
            _fromId = RidConverter.convertToJid((ORID)groupRequest.getFromUser().asVertex().getId());
            _fromName = groupRequest.getFromUser().getFirstName() + " " + groupRequest.getFromUser().getLastName();
            _toId = RidConverter.convertToJid((ORID)groupRequest.getToGroup().asVertex().getId());
            _toName = groupRequest.getToGroup().getName();
        }
        else if (request instanceof INetworkAccessRequest)
        {
            INetworkAccessRequest networkRequest = ((INetworkAccessRequest)request); 
            _requestType = "Network Access";
            _fromId = RidConverter.convertToJid((ORID)networkRequest.getFromUser().asVertex().getId());
            _fromName = networkRequest.getFromUser().getFirstName() + " " + networkRequest.getFromUser().getLastName();
            _toId = RidConverter.convertToJid((ORID)networkRequest.getToNetwork().asVertex().getId());
            _toName = networkRequest.getToNetwork().getTitle();
        }
    }

    
    
    
    public String getFrom()
    {
        return _fromName;
    }

    public void setFrom(String fromName)
    {
        _fromName = fromName;
    }

    public String getFromId()
    {
        return _fromId;
    }

    public void setFromId(String fromId)
    {
        _fromId = fromId;
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
    
    public String getResponder()
    {
        return _responder;
    }
    
    public void setResponder(String responder)
    {
        _responder = responder;
    }
    
    public String getResponse()
    {
        return _response;
    }
    
    public void setResponse(String response)
    {
        _response = response;
    }

    public String getTo()
    {
        return _toName;
    }

    public void setTo(String toName)
    {
        _toName = toName;
    }

    public String getToId()
    {
        return _toId;
    }

    public void setToId(String toId)
    {
        _toId = toId;
    }
}
