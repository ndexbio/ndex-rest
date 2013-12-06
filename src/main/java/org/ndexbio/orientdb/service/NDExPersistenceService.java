package org.ndexbio.orientdb.service;

import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.ITerm;

import com.tinkerpop.frames.VertexFrame;

public interface NDExPersistenceService {
	public ITerm findOrCreateNdexEntity( ITerm iterm);
	//TODO: replace specific methods with a single method using a generic superclass
	// that supports the JDEx ID
	public ICitation findOrCreateNdexEntity( ICitation citation);
	public IEdge findOrCreateNdexEntity( IEdge edge);
	public INamespace findOrCreateNdexEntity( INamespace namespace);
	public INode findOrCreateNdexEntity( INode node);
	public ISupport findOrCreateNdexEntity( ISupport support);
	
	
	public VertexFrame findNdexEntityByJdexId(Long jdexId);
	
	public boolean isEntityPersisted(Long jdexId);
	
	
}
