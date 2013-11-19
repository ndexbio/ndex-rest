package org.ndexbio.rest.services;

import org.ndexbio.rest.domain.XBaseTerm;
import org.ndexbio.rest.domain.XFunctionTerm;
import org.ndexbio.rest.domain.XTerm;
import org.ndexbio.rest.domain.XUser;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.exception.ODatabaseException;
import com.orientechnologies.orient.server.OServer;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule;
import com.tinkerpop.frames.modules.typedgraph.TypedGraphModuleBuilder;

public abstract class NdexService
{
    protected OServer _orientDbServer = null;
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
            _orientDbServer = new OServer();
            _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
                new TypedGraphModuleBuilder()
                    .withClass(XTerm.class)
                    .withClass(XFunctionTerm.class)
                    .withClass(XBaseTerm.class)
                    .build());
            
            //TODO: Refactor this to connect using a configurable username/password, and database
            _ndexDatabase = (ODatabaseDocumentTx)_orientDbServer.openDatabase("document", "ndex", "admin", "admin");
            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph((ODatabaseDocumentTx)_ndexDatabase));
        }
        catch (Exception e)
        {
            OLogManager.instance().error(this, "Cannot access database: " + "ndex" + ".", ODatabaseException.class, e);
        }
    }
}
