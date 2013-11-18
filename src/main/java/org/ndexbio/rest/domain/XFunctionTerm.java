package org.ndexbio.rest.domain;

import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

import java.util.Map;

/**
 * @author Andrey Lomakin <a href="mailto:lomakin.andrey@gmail.com">Andrey Lomakin</a>
 * @since 10/30/13
 */
@TypeValue("xFunctionTerm")
public interface XFunctionTerm extends XTerm {
    @Property("textParameters")
    public void setTextParameters(Map<Integer, String> textParameters);

    @Property("textParameters")
    public Map<Integer, String> getTextParameters();

    @Property("linkParameters")
    public Map<Integer, ORID> getLinkParameters();

    @Property("linkParameters")
    public void setLinkParameters(Map<Integer, ORID> linkParameters);

    @Adjacency(label = "termFunction")
    public void setTermFunction(XTerm term);

    @Adjacency(label = "termFunction")
    public XTerm getTermFunction();
}
