package org.ndexbio.rest.models;

import java.util.List;

public class Group
{
    private String _id;
    private String _name;
    private GroupProfile _profile;
    private List<Network> _networksOwned;
    
    
    
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
    
    public GroupProfile getProfile()
    {
        return _profile;
    }
    
    public void setProfile(GroupProfile profile)
    {
        _profile = profile;
    }
}
