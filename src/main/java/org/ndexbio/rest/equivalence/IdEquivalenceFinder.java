/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.equivalence;

import java.util.List;
import java.util.Map;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.Network;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;

public class IdEquivalenceFinder implements EquivalenceFinder {
	
    protected Network _target = null;
//    protected Map<String, VertexFrame> _networkIndex = null;
    protected ODatabaseDocumentTx _ndexDatabase = null;
  //  protected FramedGraph<OrientBaseGraph> _orientDbGraph = null;

	public IdEquivalenceFinder(Network target,
	//		Map<String, VertexFrame> networkIndex,
			ODatabaseDocumentTx ndexDatabase  //,
		//	FramedGraph<OrientBaseGraph> orientDbGraph
			) {

	        _target = target;
	      //  _networkIndex = networkIndex;
	        _ndexDatabase = ndexDatabase;
	       // _orientDbGraph = orientDbGraph;

	}
/*
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
	public IBaseTerm getBaseTerm(BaseTerm baseTerm, String jdexId) throws NdexException {
		IBaseTerm bt = (IBaseTerm) _networkIndex.get(jdexId);
		if (null != bt) return bt;	
		bt = findBaseTerm(baseTerm, jdexId);
		if (null != bt) {
			_networkIndex.put(jdexId,bt);
			System.out.println("found BaseTerm " + bt.getJdexId() + " " + bt.getName());
		}
		return bt;
	}
	
	public IBaseTerm findBaseTerm(BaseTerm baseTerm, String jdexId) throws NdexException {
		final List<ODocument> baseTerms = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkTerms FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE @class = 'baseTerm' AND jdexId = '" + jdexId + "' "));
		if (baseTerms.size() > 1) throw new NdexException("Found multiple baseTerms by jdexId = " + jdexId);

		for (final ODocument bt : baseTerms){
			IBaseTerm ibt = _orientDbGraph.getVertex(bt, IBaseTerm.class);
			return ibt;
		}

		return null;
	}

	@Override
	public IFunctionTerm getFunctionTerm(FunctionTerm functionTerm, String jdexId) throws NdexException {
		IFunctionTerm ft = (IFunctionTerm) _networkIndex.get(jdexId);
		if (null != ft) return ft;	
		ft = findFunctionTerm(functionTerm, jdexId);
		if (null != ft){
			_networkIndex.put(jdexId,ft);
			System.out.println("found FunctionTerm " + ft.getJdexId());
		}
		return ft;	
	}
	
	public IFunctionTerm findFunctionTerm(FunctionTerm functionTerm, String jdexId) throws NdexException {
		final List<ODocument> functionTerms = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkTerms FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE @class = 'functionTerm' AND jdexId = '" + jdexId + "' "));
		if (functionTerms.size() > 1) throw new NdexException("Found multiple functionTerms by jdexId = " + jdexId);
		for (final ODocument ft : functionTerms){
			IFunctionTerm ift =  _orientDbGraph.getVertex(ft, IFunctionTerm.class);
			return ift;
		}
		return null;
	}
	
	@Override
	public IReifiedEdgeTerm getReifiedEdgeTerm(ReifiedEdgeTerm reifiedEdgeTerm,
			String jdexId) throws NdexException {
		IReifiedEdgeTerm ret = (IReifiedEdgeTerm) _networkIndex.get(jdexId);
		if (null != ret) return ret;	
		ret = findReifiedEdgeTerm(reifiedEdgeTerm, jdexId);
		if (null != ret){
			_networkIndex.put(jdexId,ret);
			System.out.println("found ReifiedEdgeTerm " + ret.getJdexId());
		}
		return ret;	
	}
	
	public IReifiedEdgeTerm findReifiedEdgeTerm(ReifiedEdgeTerm reifiedEdgeTerm, String jdexId) throws NdexException {
		final List<ODocument> reifiedEdgeTerms = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT FROM (TRAVERSE out_networkTerms FROM " + 
				this.getTargetNetwork().asVertex().getId() + 
				" WHILE $depth < 2) WHERE @class = 'reifiedEdgeTerm' AND jdexId = '" + jdexId + "' "));
		if (reifiedEdgeTerms.size() > 1) throw new NdexException("Found multiple reifiedEdgeTerms by jdexId = " + jdexId);

		for (final ODocument ft : reifiedEdgeTerms){
			IReifiedEdgeTerm ret =  _orientDbGraph.getVertex(ft, IReifiedEdgeTerm.class);
			return ret;
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

*/
	

}
