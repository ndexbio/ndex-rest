package org.ndexbio.rest.domain;

import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;
import java.util.Map;

@TypeValue("functionTerm")
public interface IFunctionTerm extends ITerm
{
    @Property("linkParameters")
    public Map<Integer, ORID> getLinkParameters();

    @Property("linkParameters")
    public void setLinkParameters(Map<Integer, ORID> linkParameters);

    @Adjacency(label = "termFunction")
    public void setTermFunction(ITerm term);

    @Adjacency(label = "termFunction")
    public ITerm getTermFunction();

    @Property("textParameters")
    public void setTextParameters(Map<Integer, String> textParameters);

    @Property("textParameters")
    public Map<Integer, String> getTextParameters();
}
