package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.helpers.RidConverter;
import com.orientechnologies.orient.core.id.ORID;

public class Group
{
    private String _backgroundImage;
    private Date _creationDate;
    private String _description;
    private String _foregroundImage;
    private String _id;
    private String _name;
    private String _organizationName;
    private List<Network> _networksOwned;
    private String _website;

    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Group()
    {
        _networksOwned = new ArrayList<Network>();
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
        this();
        
        _backgroundImage = group.getBackgroundImg();
        _creationDate = group.getCreationDate();
        _description = group.getDescription();
        _foregroundImage = group.getForegroundImg();
        _id = RidConverter.convertToJid((ORID)group.asVertex().getId());
        _name = group.getGroupName();
        _organizationName = group.getOrganizationName();
        _website = group.getWebsite();
        
        if (loadEverything)
        {
            for (INetwork ownedNetwork : group.getOwnedNetworks())
                _networksOwned.add(new Network(ownedNetwork));
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
    
    public String getId()
    {
        return _id;
    }
    
    public void setId(String id)
    {
        _id = id;
    }
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public List<Network> getNetworksOwned()
    {
        return _networksOwned;
    }
    
    public void setNetworksOwned(List<Network> networksOwned)
    {
        _networksOwned = networksOwned;
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
    
    public void setProfile(String website)
    {
        _website = website;
    }
}
