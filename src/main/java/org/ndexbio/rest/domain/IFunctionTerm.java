package org.ndexbio.rest.domain;

import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;
import java.util.Map;

@TypeValue("functionTerm")
public interface IFunctionTerm extends ITerm
{
    @Property("termParameters")
    public Map<Integer, ITerm> getTermParameters();

    @Property("termParameters")
    public void setTermParameters(Map<Integer, ITerm> termParameters);

    @Adjacency(label = "termFunction")
    public void setTermFunction(IBaseTerm term);

    @Adjacency(label = "termFunction")
    public IBaseTerm getTermFunction();

    @Property("textParameters")
    public void setTextParameters(Map<Integer, String> textParameters);

    @Property("textParameters")
    public Map<Integer, String> getTextParameters();
}
