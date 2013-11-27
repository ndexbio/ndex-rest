package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.IRequest;

public class Group extends NdexObject
{
    private String _backgroundImage;
    private Date _creationDate;
    private String _description;
    private String _foregroundImage;
    private String _name;
    private String _organizationName;
    private List<String> _networkOwnedIdList;
    private List<Request> _requests;
    private String _website;

    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Group()
    {
        super();
        
        _networkOwnedIdList = new ArrayList<String>();
        _requests = new ArrayList<Request>();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * Doesn't load owned Networks.
    * 
    * @param group The Group with source data.
    **************************************************************************/
    public Group(IGroup group)
    {
        this(group, false);
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param group The Group with source data.
    * @param loadEverything True to load owned Networks, false to exclude
    *                       them.
    **************************************************************************/
    public Group(IGroup group, boolean loadEverything)
    {
        super(group);
        
        _networkOwnedIdList = new ArrayList<String>();
        _backgroundImage = group.getBackgroundImage();
        _creationDate = group.getCreatedDate();
        _description = group.getDescription();
        _foregroundImage = group.getForegroundImage();
        _name = group.getName();
        _organizationName = group.getOrganizationName();
        _requests = new ArrayList<Request>();
        _website = group.getWebsite();
        
        for (IRequest request : group.getRequests())
            _requests.add(new Request(request));

        if (loadEverything)
        {
            for (INetwork ownedNetwork : group.getOwnedNetworks())
                _networkOwnedIdList.add(ownedNetwork.asVertex().getId().toString());
        }
    }
    
    
    
    public String getBackgroundImage()
    {
        return _backgroundImage;
    }
    
    public void setBackgroundImage(String backgroundImage)
    {
        _backgroundImage = backgroundImage;
    }
    
    public Date getCreationDate()
    {
        return _creationDate;
    }
    
    public String getDescription()
    {
        return _description;
    }
    
    public void setDescription(String description)
    {
        _description = description;
    }
    
    public String getForegroundImage()
    {
        return _foregroundImage;
    }
    
    public void setForegroundImage(String foregroundImage)
    {
        _foregroundImage = foregroundImage;
    }
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public List<String> getNetworksOwnedIdList()
    {
        return _networkOwnedIdList;
    }
    
    public void setNetworksOwnedIdList(List<String> networksOwned)
    {
        _networkOwnedIdList = networksOwned;
    }
    
    public String getOrganizationName()
    {
        return _organizationName;
    }
    
    public void setOrganizationName(String organizationName)
    {
        _organizationName = organizationName;
    }
    
    public String getWebsite()
    {
        return _website;
    }
    
    public void setWebsite(String website)
    {
        _website = website;
    }
}
