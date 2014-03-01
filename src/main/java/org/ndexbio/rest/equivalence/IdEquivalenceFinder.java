package org.ndexbio.rest.equivalence;

import java.util.List;
import java.util.Map;

import org.ndexbio.common.models.data.IBaseTerm;
import org.ndexbio.common.models.data.ICitation;
import org.ndexbio.common.models.data.IEdge;
import org.ndexbio.common.models.data.IFunctionTerm;
import org.ndexbio.common.models.data.INamespace;
import org.ndexbio.common.models.data.INetwork;
import org.ndexbio.common.models.data.INode;
import org.ndexbio.common.models.data.ISupport;
import org.ndexbio.common.models.object.BaseTerm;
import org.ndexbio.common.models.object.Citation;
import org.ndexbio.common.models.object.Edge;
import org.ndexbio.common.models.object.FunctionTerm;
import org.ndexbio.common.models.object.Namespace;
import org.ndexbio.common.models.object.Node;
import org.ndexbio.common.models.object.Support;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.VertexFrame;

public class IdEquivalenceFinder implements EquivalenceFinder {
	
    protected INetwork _target = null;
    protected Map<String, VertexFrame> _networkIndex = null;
    protected ODatabaseDocumentTx _ndexDatabase = null;
    protected FramedGraph<OrientBaseGraph> _orientDbGraph = null;

	public IdEquivalenceFinder(INetwork target,
			Map<String, VertexFrame> networkIndex,
			ODatabaseDocumentTx ndexDatabase,
			FramedGraph<OrientBaseGraph> orientDbGraph) {

	        _target = target;
	        _networkIndex = networkIndex;
	        _ndexDatabase = ndexDatabase;
	        _orientDbGraph = orientDbGraph;

	}

	@Override
	public INetwork getTarget() {
		return _target;
	}

	@Override
	public Map<String, VertexFrame> getNetworkIndex() {
		return _networkIndex;
	}

	@Override
	public INamespace getNamespace(Namespace namespace) {
		INamespace ns = (INamespace) _networkIndex.get(namespace.getJdexId());
		if (null != ns) return ns;	
		return findNamespace(namespace);
	}
	
	private INamespace findNamespace(Namespace namespace){
		final List<ODocument> namespaces = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkNamespaces FROM " + 
				this.getTarget() + 
				" WHILE depth < 2) WHERE jdexId = '" + namespace.getJdexId() + "' "));
		for (final ODocument ns : namespaces)
			return _orientDbGraph.getVertex(ns, INamespace.class);

		return null;
		
	}

	@Override
	public IBaseTerm getBaseTerm(BaseTerm baseTerm) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFunctionTerm getFunctionTerm(FunctionTerm functionTerm) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ICitation getCitation(Citation citation) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ISupport getSupport(Support support) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public INode getNode(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IEdge getEdge(Edge edge) {
		// TODO Auto-generated method stub
		return null;
	}
	

}
