package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

import java.util.List;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
public interface XCitation extends VertexFrame {
    @Property("identifier")
    public String getIdentifier();

    @Property("identifier")
    public void setIdentifier(String identifier);

    @Property("type")
    public String getType();

    @Property("type")
    public void setType(String type);

    @Property("title")
    public String getTitle();

    @Property("title")
    public void setTitle(String title);

    @Property("contributors")
    public List<String> getContributors();

    @Property("contributors")
    public void setContributors(List<String> contributors);


    @Property("jdex_id")
    public String getJdexId();

    @Property("jdex_id")
    public void setJdexId(String jdexId);
}
