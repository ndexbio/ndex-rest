package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("user")
public interface IUser extends IAccount
{
    @Property("emailAddress")
    public String getEmailAddress();

    @Property("firstName")
    public void setEmailAddress(String emailAddress);

    @Property("firstName")
    public String getFirstName();

    @Property("firstName")
    public void setFirstName(String firstName);

    @Adjacency(label = "groups")
    public void addGroup(IGroupMembership newGroup);

    @Adjacency(label = "groups")
    public Iterable<IGroupMembership> getGroups();
    
    @Adjacency(label = "groups")
    public void removeGroup(IGroupMembership group);

    @Property("lastName")
    public String getLastName();

    @Property("lastName")
    public void setLastName(String lastName);

    @Adjacency(label = "networks")
    public void addNetwork(INetworkMembership newNetwork);
    
    @Adjacency(label = "networks")
    public Iterable<INetworkMembership> getNetworks();
    
    @Adjacency(label = "networks")
    public void removeNetwork(INetworkMembership network);

    @Property("password")
    public String getPassword();

    @Property("password")
    public void setPassword(String password);
    
    @Adjacency(label = "requests")
    public void addRequest(IRequest request);
    
    @Adjacency(label = "requests")
    public Iterable<IRequest> getRequests();
    
    @Adjacency(label = "requests")
    public void removeRequest(IRequest request);

    @Property("username")
    public String getUsername();

    @Property("username")
    public void setUsername(String username);

    @Adjacency(label = "workSurface")
    public void addNetworkToWorkSurface(INetwork network);

    @Adjacency(label = "workSurface")
    public Iterable<INetwork> getWorkSurface();

    @Adjacency(label = "workSurface")
    public void removeNetworkFromWorkSurface(INetwork network);
}
