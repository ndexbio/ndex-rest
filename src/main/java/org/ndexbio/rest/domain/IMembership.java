package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.frames.modules.typedgraph.TypeField;

@TypeField("membershipType")
public interface IMembership extends VertexFrame
{
    @Adjacency(label = "membershipMember")
    public IAccount getMember();

    @Adjacency(label = "membershipMember")
    public void setMember(IAccount member);
    
    @Property("membershipPermissions")
    public Permissions getPermissions();
    
    @Property("membershipPermissions")
    public void setPermissions(Permissions permissions);
}
