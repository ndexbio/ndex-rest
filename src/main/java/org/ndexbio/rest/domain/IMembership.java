package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.frames.modules.typedgraph.TypeField;

@TypeField("membershipType")
public interface IMembership extends VertexFrame
{
    @Adjacency(label = "member")
    public IAccount getMember();

    @Adjacency(label = "member")
    public void setMember(IAccount member);
    
    @Property("permissions")
    public Permissions getPermissions();
    
    @Property("permissions")
    public void setPermissions(Permissions permissions);
}
