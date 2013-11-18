package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.VertexFrame;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
public interface XSupport extends VertexFrame {
    @Property("jdex_id")
    public void setJdexId(String jdexId);

    @Property("jdex_id")
    public String getJdexId();

    @Property("text")
    public void setText(String text);

    @Property("text")
    public String getText();
}
