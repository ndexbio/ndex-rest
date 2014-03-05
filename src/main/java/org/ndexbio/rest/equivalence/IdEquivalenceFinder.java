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
		ns = findNamespace(namespace);
		if (null != ns) {
			_networkIndex.put(jdexId,ns);
			System.out.println("found Namespace " + ns.getJdexId() + " " + ns.getPrefix());
		}
		return ns;
	}
	
	private INamespace findNamespace(Namespace namespace){
		final List<ODocument> namespaces = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkNamespaces FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + namespace.getJdexId() + "' "));
		for (final ODocument ns : namespaces){
			INamespace ins = _orientDbGraph.getVertex(ns, INamespace.class);
			return ins;
		}

		return null;
		
	}

	@Override
	public IBaseTerm getBaseTerm(BaseTerm baseTerm, String jdexId) {
		IBaseTerm bt = (IBaseTerm) _networkIndex.get(jdexId);
		if (null != bt) return bt;	
		bt = findBaseTerm(baseTerm, jdexId);
		if (null != bt) {
			_networkIndex.put(jdexId,bt);
			System.out.println("found BaseTerm " + bt.getJdexId() + " " + bt.getName());
		}
		return bt;
	}
	
	public IBaseTerm findBaseTerm(BaseTerm baseTerm, String jdexId) {
		final List<ODocument> baseTerms = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkTerms FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE @class = 'baseTerm' AND jdexId = '" + jdexId + "' "));
		for (final ODocument bt : baseTerms){
			IBaseTerm ibt = _orientDbGraph.getVertex(bt, IBaseTerm.class);
			return ibt;
		}

		return null;
	}

	@Override
	public IFunctionTerm getFunctionTerm(FunctionTerm functionTerm, String jdexId) {
		IFunctionTerm ft = (IFunctionTerm) _networkIndex.get(jdexId);
		if (null != ft) return ft;	
		ft = findFunctionTerm(functionTerm, jdexId);
		if (null != ft){
			_networkIndex.put(jdexId,ft);
			System.out.println("found FunctionTerm " + ft.getJdexId());
		}
		return ft;	
	}
	
	public IFunctionTerm findFunctionTerm(FunctionTerm functionTerm, String jdexId) {
		final List<ODocument> functionTerms = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkTerms FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE @class = 'functionTerm' AND jdexId = '" + jdexId + "' "));
		for (final ODocument ft : functionTerms){
			IFunctionTerm ift =  _orientDbGraph.getVertex(ft, IFunctionTerm.class);
			return ift;
		}
		return null;
	}

	@Override
	public ICitation getCitation(Citation citation, String jdexId) {
		ICitation cit = (ICitation) _networkIndex.get(jdexId);
		if (null != cit) return cit;	
		cit = findCitation(citation, jdexId);
		if (null != cit){
			_networkIndex.put(jdexId,cit);
			System.out.println("found Citation " + cit.getJdexId());
		}
		return cit;
	}
	
	public ICitation findCitation(Citation citation, String jdexId) {
		final List<ODocument> citations = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkCitations FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId+ "' "));
		for (final ODocument cit : citations){
			ICitation iCitation = (ICitation) _orientDbGraph.getVertex(cit, ICitation.class);
			return iCitation;
		}

		return null;
	}

	@Override
	public ISupport getSupport(Support support, String jdexId) {
		ISupport sup = (ISupport) _networkIndex.get(jdexId);
		if (null != sup) return sup;	
		sup = findSupport(support, jdexId);
		if (null != sup){
			_networkIndex.put(jdexId,sup);
			System.out.println("found Support " + sup.getJdexId());
		}
		return sup;
	}
	
	public ISupport findSupport(Support support, String jdexId) {
		final List<ODocument> supports = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkSupports FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId + "' "));
		for (final ODocument sup : supports){
			ISupport iSupport = _orientDbGraph.getVertex(sup, ISupport.class);
			return iSupport;
		}

		return null;
	}

	@Override
	public INode getNode(Node node, String jdexId) {
		INode n = (INode) _networkIndex.get(jdexId);
		if (null != n) return n;	
		n = findNode(node, jdexId);
		if (null != n) {
			_networkIndex.put(jdexId,n);
			System.out.println("found Node " + n.getJdexId());
		}
		return n;
	}
	
	public INode findNode(Node node, String jdexId) {	
		final List<ODocument> nodes = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkNodes FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId + "' "));
		for (final ODocument n : nodes){
			INode iNode = _orientDbGraph.getVertex(n, INode.class);
			return iNode;
		}

		return null;
	}

	@Override
	public IEdge getEdge(Edge edge, String jdexId) {
		IEdge e = (IEdge) _networkIndex.get(jdexId);
		if (null != e) return e;	
		e = findEdge(edge, jdexId);
		if (null != e){
			_networkIndex.put(jdexId,e);
			System.out.println("found Edge " + e.getJdexId());
		}
		return e;
	}
	
	public IEdge findEdge(Edge edge, String jdexId) {
		final List<ODocument> edges = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkEdges FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE jdexId = '" + jdexId + "' "));
		for (final ODocument e : edges){
			IEdge iEdge = (IEdge) _orientDbGraph.getVertex(e, IEdge.class);
			return iEdge;
		}

		return null;
	}
	

}
