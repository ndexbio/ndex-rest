package org.ndexbio.rest.domain;

import com.tinkerpop.blueprints.Direction;
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

    @Property("lastName")
    public String getLastName();

    @Property("lastName")
    public void setLastName(String lastName);

    @Adjacency(label = "ownedGroups", direction = Direction.OUT)
    public void addOwnedGroup(IGroup group);

    @Adjacency(label = "ownedGroups", direction = Direction.OUT)
    public Iterable<IGroup> getOwnedGroups();
    
    @Adjacency(label = "ownedGroups", direction = Direction.OUT)
    public void removeOwnedGroup(IGroup group);

    @Adjacency(label = "ownedNetworks", direction = Direction.OUT)
    public void addOwnedNetwork(INetwork network);

    @Adjacency(label = "ownedNetworks", direction = Direction.OUT)
    public Iterable<INetwork> getOwnedNetworks();

    @Adjacency(label = "ownedNetworks", direction = Direction.OUT)
    public void removeOwnedNetwork(INetwork network);

    @Property("password")
    public String getPassword();

    @Property("password")
    public void setPassword(String password);
    
    @Adjacency(label = "requests", direction = Direction.OUT)
    public Iterable<IRequest> getRequests();
    
    @Adjacency(label = "requests", direction = Direction.OUT)
    public void setRequests(Iterable<IRequest> requests);

    @Property("username")
    public String getUsername();

    @Property("username")
    public void setUsername(String username);

    @Property("website")
    public void setWebsite(String website);

    @Property("website")
    public String getWebsite();

    @Adjacency(label = "workSurface", direction = Direction.OUT)
    public void addNetworkToWorkSurface(INetwork network);

    @Adjacency(label = "workSurface", direction = Direction.OUT)
    public Iterable<INetwork> getWorkSurface();

    @Adjacency(label = "workSurface", direction = Direction.OUT)
    public void removeNetworkFromWorkSurface(INetwork network);
}
