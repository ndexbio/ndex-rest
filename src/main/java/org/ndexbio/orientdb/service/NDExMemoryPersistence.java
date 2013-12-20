package org.ndexbio.orientdb.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.IdConverter;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.xbel.cache.XbelCacheService;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

/*
 * An implementation of the NDExPersistenceService interface that uses a 
 * in-memory cache to provide persistence for new ndex doain objects
 * 
 * 
 * Utilizes a Google Guava cache that uses Jdexids as keys and VertexFrame implementations 
 * as values
 */

public enum NDExMemoryPersistence implements NDExPersistenceService {
	
	INSTANCE;
	 
	 private NdexService ndexService = new NdexService();
	 private Set<Long> jdexIdSet = Sets.newHashSet();
	 private INetwork network;
	 private IUser user;
	 
	 //IBaseTerm cache
	 private LoadingCache<Long, IBaseTerm> baseTermCache = CacheBuilder.newBuilder()
			 .maximumSize(10000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,IBaseTerm>() {
				@Override
				public IBaseTerm load(Long key) throws Exception {
					return ndexService._orientDbGraph.addVertex("class:baseTerm", IBaseTerm.class);
				}
			 });
	 
	 //IFunctionTerm cache
	 private LoadingCache<Long, IFunctionTerm> functionTermCache = CacheBuilder.newBuilder()
			 .maximumSize(10000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,IFunctionTerm>() {
				@Override
				public IFunctionTerm load(Long key) throws Exception {
					return ndexService._orientDbGraph.addVertex("class:functionTerm", IFunctionTerm.class);
				}
				 
			 });
	 
	 //INamespace cache
	 private LoadingCache<Long, INamespace> namespaceCache = CacheBuilder.newBuilder()
			 .maximumSize(10000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,INamespace>() {
				@Override
				public INamespace load(Long key) throws Exception {
					return  ndexService._orientDbGraph.addVertex("class:namespace", INamespace.class);
				}
				 
			 });
	 
	 
	 //ICitation cache
	 private LoadingCache<Long, ICitation> citationCache = CacheBuilder.newBuilder()
			 .maximumSize(10000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,ICitation>() {
				@Override
				public ICitation load(Long key) throws Exception {
					return  ndexService._orientDbGraph.addVertex("class:citation", ICitation.class);
				}
				 
			 });
	 
	//IEdge cache
		 private LoadingCache<Long, IEdge> edgeCache = CacheBuilder.newBuilder()
				 .maximumSize(10000L)
				 .expireAfterAccess(240L, TimeUnit.MINUTES)
				
				 .build(new CacheLoader<Long,IEdge>() {
					@Override
					public IEdge load(Long key) throws Exception {
						return  ndexService._orientDbGraph.addVertex("class:edge", IEdge.class);
					}
					 
				 });
		 
		//INode cache
		 private LoadingCache<Long, INode> nodeCache = CacheBuilder.newBuilder()
				 .maximumSize(10000L)
				 .expireAfterAccess(240L, TimeUnit.MINUTES)
				
				 .build(new CacheLoader<Long,INode>() {
					@Override
					public INode load(Long key) throws Exception {
						return  ndexService._orientDbGraph.addVertex("class:node", INode.class);
					}
					 
				 });
		 
		//ISupport cache
		 private LoadingCache<Long, ISupport> supportCache = CacheBuilder.newBuilder()
				 .maximumSize(10000L)
				 .expireAfterAccess(240L, TimeUnit.MINUTES)
				
				 .build(new CacheLoader<Long,ISupport>() {
					@Override
					public ISupport load(Long key) throws Exception {
						return  ndexService._orientDbGraph.addVertex("class:support", ISupport.class);
					}
					 
				 });
	 
	 

	@Override
	public boolean isEntityPersisted(Long jdexId) {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		return (this.jdexIdSet.contains(jdexId));
		
	}
	
	
	@Override
	// To find a namespace by its prefix, first try to find a jdexid by looking up the prefix in the identifier cache.
	// If a jdexid is found, then lookup the INamespace by jdexid in the namespaceCache and return it.
	public INamespace findNamespaceByPrefix(String prefix) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(prefix), "A namespace prefix is required");
		Preconditions.checkArgument(!XbelCacheService.INSTANCE.isNovelIdentifier(prefix),
				"The namespace prefix " + prefix +" is not registered");
		try {
			Long jdexId = XbelCacheService.INSTANCE.accessIdentifierCache().get(prefix);
			INamespace ns = this.namespaceCache.getIfPresent(jdexId);
			return ns;
		} catch (ExecutionException e) {
			
			e.printStackTrace();
		}
		return null;
	}
	
	// find 


	@Override
	public IBaseTerm findOrCreateIBaseTerm(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return baseTermCache.get(jdexId);
	}


	@Override
	public IFunctionTerm findOrCreateIFunctionTerm(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return functionTermCache.get(jdexId);
	}


	@Override
	public INamespace findOrCreateINamespace(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return namespaceCache.get(jdexId);
	}


	@Override
	public ICitation findOrCreateICitation(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return citationCache.get(jdexId);
	}

	@Override
	public IEdge findOrCreateIEdge(Long jdexId) throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return edgeCache.get(jdexId);
	}


	@Override
	public INode findOrCreateINode(Long jdexId) throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return nodeCache.get(jdexId);
	}


	@Override
	public ISupport findOrCreateISupport(Long jdexId) throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return supportCache.get(jdexId);
	}


	@Override
	 public INetwork createNetwork(Network newNetwork) throws Exception
	    {
	        Preconditions.checkArgument(null != newNetwork,"A network model object is required");
	        Preconditions.checkArgument(null != newNetwork.getMembers() && 
	        		newNetwork.getMembers().size() > 0,
	        		"The network to create has no members specified.");
	        
	        try
	        {
	           
	            final Membership newNetworkMembership = newNetwork.getMembers().get(0);
	            final ORID userRid = IdConverter.toRid(newNetworkMembership.getResourceId());

	            final IUser networkOwner =  ndexService._orientDbGraph.getVertex(userRid, IUser.class);
	            if (networkOwner == null)
	                throw new ObjectNotFoundException("User", newNetworkMembership.getResourceId());

	            final INetwork network =  ndexService._orientDbGraph.addVertex("class:network", INetwork.class);

	            final INetworkMembership membership =  ndexService._orientDbGraph.addVertex("class:networkMembership", INetworkMembership.class);
	            membership.setPermissions(Permissions.ADMIN);
	            membership.setMember(networkOwner);
	            membership.setNetwork(network);
	            networkOwner.addNetwork(membership);
	            network.addMember(membership);
	            network.setIsPublic(false);
	            network.setFormat(newNetwork.getFormat());
	            network.setSource(newNetwork.getSource());
	            network.setTitle(newNetwork.getTitle());
	           this.network = network;  // keep a copy in this repository
	            return network;
	        }catch (Exception e)
	        {
	        	 ndexService._orientDbGraph.getBaseGraph().rollback();
	            throw e;
	        }
	    }
	    @Override
		public INetwork getCurrentNetwork() {
	    	if (null == this.network){
	    		this.network = ndexService._orientDbGraph.addVertex("class:network", INetwork.class);
	    	}
	    	
	    	return this.network;}

	    /*
	     * find the ITerm (either Base or Function) by jdex id
	     */
		@Override
		public ITerm findChildITerm(Long jdexId) throws ExecutionException {
			Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
					"A valid JDExId is required");
			return Objects.firstNonNull((ITerm) this.baseTermCache.getIfPresent(jdexId),
					(ITerm) this.functionTermCache.getIfPresent(jdexId));
	
		}


		@Override
		public IUser getCurrentUser() {
			if (null == this.user) {
				this.user = ndexService._orientDbGraph.addVertex("class:user", IUser.class);
			}
			return this.user;
		}


		@Override
		public INetworkMembership createNetworkMembership() {
			
			return ndexService._orientDbGraph.addVertex("class:networkMembership", INetworkMembership.class);
		}
	
		/*
		 * Returns a collection of IUsers based on search criteria
		 */
		@Override
		 public List<IUser> findUsers(SearchParameters searchParameters) throws NdexException
		    {
		        if (searchParameters.getSearchString() == null || searchParameters.getSearchString().isEmpty())
		            throw new IllegalArgumentException("No search string was specified.");
		        else
		            searchParameters.setSearchString(searchParameters.getSearchString().toUpperCase().trim());
		        
		        final List<IUser> foundUsers = Lists.newArrayList();
		        
		        final int startIndex = searchParameters.getSkip() * searchParameters.getTop();

		        String whereClause = " where username.toUpperCase() like '%" + searchParameters.getSearchString()
		                    + "%' OR lastName.toUpperCase() like '%" + searchParameters.getSearchString()
		                    + "%' OR firstName.toUpperCase() like '%" + searchParameters.getSearchString() + "%'";

		        final String query = "select from User " + whereClause
		                + " order by creation_date desc skip " + startIndex + " limit " + searchParameters.getTop();
		        
		        try
		        {
		           
		            
		            List<ODocument> userDocumentList = ndexService._orientDbGraph
		                .getBaseGraph()
		                .getRawGraph()
		                .query(new OSQLSynchQuery<ODocument>(query));
		            
		            for (final ODocument document : userDocumentList)
		                foundUsers.add(ndexService._orientDbGraph.getVertex(document, IUser.class));
		            
		            return foundUsers;
		        }
		        catch (Exception e)
		        {
		        	ndexService._orientDbGraph.getBaseGraph().rollback(); 
		            throw e;
		        }
		        
		    }
		/*
		 * public method to allow xbel parsing components to rollback the transaction and 
		 * close the database connection if they encounter an error situation
		 */

		@Override
		public void abortTransaction() {
			System.out.println(this.getClass().getName() +".abortTransaction has been invoked.");
			try {
				ndexService._orientDbGraph.getBaseGraph().rollback();
				System.out.println("The current orientdb transaction has been rolled back");
			} finally {
				ndexService.teardownDatabase();
				System.out.println("Connection to orientdb database has been closed");
			}
		}


	
		/*
		 * public method to persist INetwork to the orientdb database
		 * using cache contents.
		 */

		@Override
		public void persistNetwork() {
			try {

				//1. namespaces
				this.addINamespaces();
				//2. terms
				this.addITerms();
				//3. nodes
				this.addINodes();
				//4. edges
				this.addIEdges();
				//5. citations
				this.addICitations();
				//6. supports
				this.addISupports();
				// commit

				ndexService._orientDbGraph.getBaseGraph().commit();
				System.out.println("The new network " +network.getTitle() 
						+" has been committed");
			} catch (Exception e) {
				ndexService._orientDbGraph.getBaseGraph().rollback();
				System.out.println("The current orientdb transaction has been rolled back");
				e.printStackTrace();
			} finally {
				ndexService.teardownDatabase();
				System.out.println("Connection to orientdb database has been closed");
			}
		}
		
		private void addISupports() {
			for(ISupport support : this.supportCache.asMap().values()){
				this.network.addSupport(support);
			}
		}
		
		private void addICitations() {
			for(ICitation citation : this.citationCache.asMap().values()){
				this.network.addCitation(citation);
			}
		}
		
		private void addIEdges() {
			for (IEdge edge : this.edgeCache.asMap().values()) {
				this.network.addNdexEdge(edge);
			}
			this.network.setNdexEdgeCount(this.edgeCache.asMap().size());
		}
		
		
		
		private void addINodes() {
			for (INode in : this.nodeCache.asMap().values()){
				this.network.addNdexNode(in);
			}
			this.network.setNdexNodeCount(this.nodeCache.asMap().size());
		}
		
		private void addINamespaces() {
			for(INamespace ns : this.namespaceCache.asMap().values()){
				this.network.addNamespace(ns);
			}
		}
		
		private void addITerms() {		
			for (IBaseTerm bt : this.baseTermCache.asMap().values() ){
				this.network.addTerm(bt);
			}
			for(IFunctionTerm ft : this.functionTermCache.asMap().values()){
				this.network.addTerm(ft);
			}					
		}



	






}
