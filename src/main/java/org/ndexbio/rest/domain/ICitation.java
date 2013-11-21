package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import java.util.List;

public interface ICitation extends VertexFrame
{
    @Property("contributors")
    public List<String> getContributors();

    @Property("contributors")
    public void setContributors(List<String> contributors);

    @Property("identifier")
    public String getIdentifier();

    @Property("identifier")
    public void setIdentifier(String identifier);

    @Property("jdexId")
    public String getJdexId();

    @Property("jdexId")
    public void setJdexId(String jdexId);

    @Property("title")
    public String getTitle();

    @Property("title")
    public void setTitle(String title);

    @Property("type")
    public String getType();

    @Property("type")
    public void setType(String type);
}
