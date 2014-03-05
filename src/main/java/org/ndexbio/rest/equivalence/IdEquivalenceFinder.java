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
	public INetwork getTargetNetwork() {
		return _target;
	}

	@Override
	public Map<String, VertexFrame> getNetworkIndex() {
		return _networkIndex;
	}

	@Override
	public INamespace getNamespace(Namespace namespace, String jdexId) {
		INamespace ns = (INamespace) _networkIndex.get(jdexId);
		if (null != ns) return ns;	
		return findNamespace(namespace);
	}
	
	private INamespace findNamespace(Namespace namespace){
		final List<ODocument> namespaces = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkNamespaces FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + namespace.getJdexId() + "' "));
		for (final ODocument ns : namespaces)
			return _orientDbGraph.getVertex(ns, INamespace.class);

		return null;
		
	}

	@Override
	public IBaseTerm getBaseTerm(BaseTerm baseTerm, String jdexId) {
		IBaseTerm bt = (IBaseTerm) _networkIndex.get(jdexId);
		if (null != bt) return bt;	
		return findBaseTerm(baseTerm, jdexId);
	}
	
	public IBaseTerm findBaseTerm(BaseTerm baseTerm, String jdexId) {
		final List<ODocument> baseTerms = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkTerms FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE @class = 'baseTerm' AND jdexId = '" + jdexId + "' "));
		for (final ODocument bt : baseTerms)
			return _orientDbGraph.getVertex(bt, IBaseTerm.class);

		return null;
	}

	@Override
	public IFunctionTerm getFunctionTerm(FunctionTerm functionTerm, String jdexId) {
		IFunctionTerm ft = (IFunctionTerm) _networkIndex.get(jdexId);
		if (null != ft) return ft;	
		return findFunctionTerm(functionTerm, jdexId);
	}
	
	public IFunctionTerm findFunctionTerm(FunctionTerm functionTerm, String jdexId) {
		final List<ODocument> functionTerms = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkTerms FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE @class = 'functionTerm' AND jdexId = '" + jdexId + "' "));
		for (final ODocument ft : functionTerms)
			return _orientDbGraph.getVertex(ft, IFunctionTerm.class);

		return null;
	}

	@Override
	public ICitation getCitation(Citation citation, String jdexId) {
		ICitation cit = (ICitation) _networkIndex.get(jdexId);
		if (null != cit) return cit;	
		return findCitation(citation, jdexId);
	}
	
	public ICitation findCitation(Citation citation, String jdexId) {
		final List<ODocument> citations = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkCitations FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId+ "' "));
		for (final ODocument cit : citations)
			return _orientDbGraph.getVertex(cit, ICitation.class);

		return null;
	}

	@Override
	public ISupport getSupport(Support support, String jdexId) {
		ISupport sup = (ISupport) _networkIndex.get(jdexId);
		if (null != sup) return sup;	
		return findSupport(support, jdexId);
	}
	
	public ISupport findSupport(Support support, String jdexId) {
		final List<ODocument> supports = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkSupports FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId + "' "));
		for (final ODocument sup : supports)
			return _orientDbGraph.getVertex(sup, ISupport.class);

		return null;
	}

	@Override
	public INode getNode(Node node, String jdexId) {
		INode n = (INode) _networkIndex.get(jdexId);
		if (null != n) return n;	
		return findNode(node, jdexId);
	}
	
	public INode findNode(Node node, String jdexId) {	
		final List<ODocument> nodes = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkNodes FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId + "' "));
		for (final ODocument n : nodes)
			return _orientDbGraph.getVertex(n, INode.class);

		return null;
	}

	@Override
	public IEdge getEdge(Edge edge, String jdexId) {
		IEdge e = (IEdge) _networkIndex.get(jdexId);
		if (null != e) return e;	
		return findEdge(edge, jdexId);
	}
	
	public IEdge findEdge(Edge edge, String jdexId) {
		final List<ODocument> edges = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM (TRAVERSE out_networkEdges FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId + "' "));
		for (final ODocument e : edges)
			return _orientDbGraph.getVertex(e, IEdge.class);

		return null;
	}
	

}
