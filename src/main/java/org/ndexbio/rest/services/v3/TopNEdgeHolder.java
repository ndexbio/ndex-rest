package org.ndexbio.rest.services.v3;

import java.util.TreeSet;

import org.ndexbio.cx2.aspect.element.core.CxEdge;

public class TopNEdgeHolder {
	
	private int limit;
	
	private TreeSet<CxEdge> edges;
	
	private EdgeFilterComparator comparator;
	
	public TopNEdgeHolder (int limit, EdgeFilterComparator comparator) {
		this.limit = limit;
		this.edges = new TreeSet<>(comparator);
		this.comparator = comparator;
	}

	public void addEdge (CxEdge edge) {
		if ( limit <= 0 || edges.size() < limit ) {
			edges.add(edge);
		} else if ( comparator.compare(edge, edges.last()) < 0 ) {
			edges.pollLast();
			edges.add(edge);
		}
	}
	
	public TreeSet<CxEdge> getEdges() {
		return edges;
	}
	
	

}
