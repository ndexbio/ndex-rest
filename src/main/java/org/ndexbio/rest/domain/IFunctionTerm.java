package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("Function")
public interface IFunctionTerm extends ITerm
{
    @Adjacency(label = "termParameters")
    public Iterable<ITerm> getTermParameters();

    @Adjacency(label = "termParameters")
    public void setTermParameters(Iterable< ITerm> termParameters);

    @Adjacency(label = "termFunc")
    public IBaseTerm getTermFunc();

    @Adjacency(label = "termFunc")
    public void setTermFunc(IBaseTerm term);

    
}
