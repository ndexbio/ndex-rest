package org.ndexbio.rest.domain;

import java.util.Date;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;
import com.tinkerpop.frames.modules.typedgraph.TypeField;

@TypeField("accountType")
public interface IAccount extends VertexFrame
{
    @Property("backgroundImage")
    public void setBackgroundImage(String image);

    @Property("backgroundImage")
    public String getBackgroundImage();

    @Property("createdDate")
    public void setCreatedDate(Date date);

    @Property("createdDate")
    public Date getCreatedDate();

    @Property("description")
    public String getDescription();

    @Property("description")
    public void setDescription(String description);

    @Property("foregroundImage")
    public void setForegroundImage(String image);

    @Property("foregroundImage")
    public String getForegroundImage();
}
