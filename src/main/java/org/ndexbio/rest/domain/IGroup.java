package org.ndexbio.rest.domain;

import java.util.Map;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("group")
public interface IGroup extends IAccount
{
    @Property("name")
    void setName(String name);

    @Property("name")
    String getName();
    
    @Adjacency(label = "members", direction = Direction.OUT)
    public void addMember(IUser user);
    
    @Adjacency(label = "members", direction = Direction.OUT)
    public Iterable<IUser> getMembers();
    
    @Adjacency(label = "members", direction = Direction.OUT)
    public void removeMember(IUser user);

    @Property("organizationName")
    void setOrganizationName(String organizationName);

    @Property("organizationName")
    String getOrganizationName();
    
    @Adjacency(label = "ownedNetworks", direction = Direction.OUT)
    public void addOwnedNetwork(INetwork network);

    @Adjacency(label = "ownedNetworks", direction = Direction.OUT)
    public Iterable<INetwork> getOwnedNetworks();
    
    @Adjacency(label = "ownedNetworks", direction = Direction.OUT)
    public void removeOwnedNetwork(INetwork network);

    @Adjacency(label = "owners", direction = Direction.IN)
    public Iterable<IUser> getOwners();
    
    @Adjacency(label = "requests", direction = Direction.IN)
    public Iterable<IRequest> getRequests(Map<String, String> properties);
    
    @Adjacency(label = "requests", direction = Direction.IN)
    public void setRequests(Iterable<IRequest> requests);

    @Property("website")
    void setWebsite(String website);

    @Property("website")
    String getWebsite();
}
