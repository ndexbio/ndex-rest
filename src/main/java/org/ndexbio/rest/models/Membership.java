package org.ndexbio.rest.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.helpers.RidConverter;
import com.orientechnologies.orient.core.id.ORID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Membership
{
    private Permissions _memberPermissions;
    private String _resourceId;
    private String _resourceName;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Membership()
    {
        
    }
    
    /**************************************************************************
    * Populates the class with user membership (from the database).
    * 
    * @param membership A key/value pair containing the object/permissions.
    **************************************************************************/
    public Membership(IUser user, Permissions permissions)
    {
        _memberPermissions = permissions;
        _resourceId = RidConverter.convertToJid((ORID)user.asVertex().getId());
        _resourceName = user.getFirstName() + " " + user.getLastName();
    }
    
    /**************************************************************************
    * Populates the class with group membership (from the database).
    * 
    * @param membership A key/value pair containing the object/permissions.
    **************************************************************************/
    public Membership(IGroup group, Permissions permissions)
    {
        _memberPermissions = permissions;
        _resourceId = RidConverter.convertToJid((ORID)group.asVertex().getId());
        _resourceName = group.getName();
    }
    
    /**************************************************************************
    * Populates the class with network membership (from the database).
    * 
    * @param membership A key/value pair containing the object/permissions.
    **************************************************************************/
    public Membership(INetwork network, Permissions permissions)
    {
        _memberPermissions = permissions;
        _resourceId = RidConverter.convertToJid((ORID)network.asVertex().getId());
        _resourceName = network.getTitle();
    }
    
    
    
    public Permissions getPermissions()
    {
        return _memberPermissions;
    }
    
    public void setPermissions(Permissions memberPermissions)
    {
        _memberPermissions = memberPermissions;
    }
    
    public String getResourceId()
    {
        return _resourceId;
    }
    
    public void setResourceId(String resourceId)
    {
        _resourceId = resourceId;
    }
    
    public String getResourceName()
    {
        return _resourceName;
    }
    
    public void setResourceName(String resourceName)
    {
        _resourceName = resourceName;
    }
}
