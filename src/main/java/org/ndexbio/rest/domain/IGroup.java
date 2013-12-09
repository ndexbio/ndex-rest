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
    
    @Adjacency(label = "members")
    public void addMember(IGroupMembership newMember);
    
    @Adjacency(label = "members")
    public Iterable<IGroupMembership> getMembers();
    
    @Adjacency(label = "members")
    public void removeMember(IGroupMembership member);

    @Property("organizationName")
    void setOrganizationName(String organizationName);

    @Property("organizationName")
    String getOrganizationName();

    @Adjacency(label = "networks")
    public void addNetwork(INetworkMembership newNetwork);
    
    @Adjacency(label = "networks")
    public Iterable<INetworkMembership> getNetworks();
    
    @Adjacency(label = "networks")
    public void removeNetwork(INetworkMembership network);
    
    @Adjacency(label = "requests")
    public void addRequest(IRequest request);
    
    @Adjacency(label = "requests")
    public Iterable<IRequest> getRequests();
    
    @Adjacency(label = "requests")
    public void removeRequest(IRequest request);
}
