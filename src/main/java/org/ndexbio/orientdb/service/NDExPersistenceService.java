package org.ndexbio.orientdb.service;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.SearchParameters;

/*
 * public interface representing all interactions with the underlying persistence implementation
 * this may be either direct interaction with a graph database or an in-memory domain
 * object caches
 */

public interface NDExPersistenceService {
	
	// find or create instances of domain objects
	public ITerm findChildITerm( Long jdexId) throws ExecutionException;
	public IBaseTerm findOrCreateIBaseTerm( Long jdexId) throws ExecutionException;
	public IFunctionTerm findOrCreateIFunctionTerm( Long jdexId) throws ExecutionException;
	public INamespace findOrCreateINamespace( Long jdexId) throws ExecutionException;
	public ICitation findOrCreateICitation( Long jdexId) throws ExecutionException;
	public IEdge findOrCreateIEdge( Long jdexId) throws ExecutionException;
	public INode findOrCreateINode( Long jdexId) throws ExecutionException;
	public ISupport findOrCreateISupport( Long jdexId) throws ExecutionException;
	
	public void persistNetwork();
	
	
	public boolean isEntityPersisted(Long jdexId);
	
	// Convenience methods
	// find an INamespace by its XBEL prefix
	public INamespace findNamespaceByPrefix(String prefix);
	public INetwork createNetwork(Network newNetwork) throws Exception;
	public INetwork getCurrentNetwork();
	public IUser getCurrentUser();
	public INetworkMembership  createNetworkMembership();
	
	public List<IUser> findUsers(SearchParameters searchParameters) throws NdexException;
	public void abortTransaction();
	
	
	
	
}
