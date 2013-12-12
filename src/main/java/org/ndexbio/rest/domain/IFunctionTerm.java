package org.ndexbio.rest.domain;

import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.Property;
import com.tinkerpop.frames.modules.typedgraph.TypeValue;

@TypeValue("Function")
public interface IFunctionTerm extends ITerm
{
    @Adjacency(label = "functionTermParameters")
    public Iterable<ITerm> getTermParameters();

    @Adjacency(label = "functionTermParameters")
    public void setTermParameters(Iterable< ITerm> termParameters);

    @Adjacency(label = "functionTermFunction")
    public IBaseTerm getTermFunc();

    @Adjacency(label = "functionTermFunction")
    public void setTermFunc(IBaseTerm term);

    
}
