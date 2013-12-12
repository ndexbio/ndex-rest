package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("user")
public interface IUser extends IAccount
{
    @Property("emailAddress")
    public String getEmailAddress();

    @Property("emailAddress")
    public void setEmailAddress(String emailAddress);

    @Property("firstName")
    public String getFirstName();

    @Property("firstName")
    public void setFirstName(String firstName);

    @Adjacency(label = "userGroups")
    public void addGroup(IGroupMembership newGroup);

    @Adjacency(label = "userGroups")
    public Iterable<IGroupMembership> getGroups();
    
    @Adjacency(label = "userGroups")
    public void removeGroup(IGroupMembership group);

    @Property("lastName")
    public String getLastName();

    @Property("lastName")
    public void setLastName(String lastName);

    @Adjacency(label = "userNetworks")
    public void addNetwork(INetworkMembership newNetwork);
    
    @Adjacency(label = "userNetworks")
    public Iterable<INetworkMembership> getNetworks();
    
    @Adjacency(label = "userNetworks")
    public void removeNetwork(INetworkMembership network);

    @Property("password")
    public String getPassword();

    @Property("password")
    public void setPassword(String password);
    
    @Adjacency(label = "userRequests")
    public void addRequest(IRequest request);
    
    @Adjacency(label = "userRequests")
    public Iterable<IRequest> getRequests();
    
    @Adjacency(label = "userRequests")
    public void removeRequest(IRequest request);

    @Adjacency(label = "userTasks")
    public void addTask(ITask network);

    @Adjacency(label = "userTasks")
    public Iterable<ITask> getTasks();

    @Adjacency(label = "userTasks")
    public void removeTask(ITask task);

    @Property("username")
    public String getUsername();

    @Property("username")
    public void setUsername(String username);

    @Adjacency(label = "userWorkSurface")
    public void addNetworkToWorkSurface(INetwork network);

    @Adjacency(label = "userWorkSurface")
    public Iterable<INetwork> getWorkSurface();

    @Adjacency(label = "userWorkSurface")
    public void removeNetworkFromWorkSurface(INetwork network);
}
