package org.ndexbio.rest.services;

import org.ndexbio.rest.NdexSchemaManager;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.ITerm;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

public abstract class NdexService
{
    protected FramedGraphFactory _graphFactory = null;
    protected ODatabaseDocumentTx _ndexDatabase = null;
    protected FramedGraph<OrientBaseGraph> _orientDbGraph = null;
    
    /**************************************************************************
    * Opens a connection to OrientDB and initializes the OrientDB Graph ORM.
    **************************************************************************/
    public NdexService()
    {
        try
        {
            //TODO: Refactor this to connect using a configurable username/password, and database
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");

            _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
                new TypedGraphModuleBuilder()
                    .withClass(ITerm.class)
                    .withClass(IFunctionTerm.class)
                    .withClass(IBaseTerm.class)
                    .build());
            
            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
            NdexSchemaManager.INSTANCE.init(_orientDbGraph.getBaseGraph());
        }
        catch (Exception e)
        {
            OLogManager.instance().error(this, "Cannot access database: " + "ndex" + ".", ODatabaseException.class, e);
        }
    }
}
