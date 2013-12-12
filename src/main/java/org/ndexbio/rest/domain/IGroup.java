package org.ndexbio.rest.domain;

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
    
    @Adjacency(label = "groupMembers")
    public void addMember(IGroupMembership newMember);
    
    @Adjacency(label = "groupMembers")
    public Iterable<IGroupMembership> getMembers();
    
    @Adjacency(label = "groupMembers")
    public void removeMember(IGroupMembership member);

    @Property("organizationName")
    void setOrganizationName(String organizationName);

    @Property("organizationName")
    String getOrganizationName();

    @Adjacency(label = "groupNetworks")
    public void addNetwork(INetworkMembership newNetwork);
    
    @Adjacency(label = "groupNetworks")
    public Iterable<INetworkMembership> getNetworks();
    
    @Adjacency(label = "groupNetworks")
    public void removeNetwork(INetworkMembership network);
    
    @Adjacency(label = "groupRequests")
    public void addRequest(IRequest request);
    
    @Adjacency(label = "groupRequests")
    public Iterable<IRequest> getRequests();
    
    @Adjacency(label = "groupRequests")
    public void removeRequest(IRequest request);
}
