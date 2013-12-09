package org.ndexbio.orientdb.service;

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
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Network;
import org.ndexbio.xbel.cache.XbelCacheService;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.orientechnologies.orient.core.id.ORID;

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
	 
	 //IBaseTerm cache
	 private LoadingCache<Long, IBaseTerm> baseTermCcahe = CacheBuilder.newBuilder()
			 .maximumSize(1000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,IBaseTerm>() {
				@Override
				public IBaseTerm load(Long key) throws Exception {
					return ndexService. _orientDbGraph.addVertex("class:baseTerm", IBaseTerm.class);
				}
				 
			 });
	 
	 //IFunctionTerm cache
	 private LoadingCache<Long, IFunctionTerm> functionTermCcahe = CacheBuilder.newBuilder()
			 .maximumSize(1000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,IFunctionTerm>() {
				@Override
				public IFunctionTerm load(Long key) throws Exception {
					return ndexService. _orientDbGraph.addVertex("class:functionTerm", IFunctionTerm.class);
				}
				 
			 });
	 
	 //INamespace cache
	 private LoadingCache<Long, INamespace> namespaceCcahe = CacheBuilder.newBuilder()
			 .maximumSize(1000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,INamespace>() {
				@Override
				public INamespace load(Long key) throws Exception {
					return  ndexService._orientDbGraph.addVertex("class:namespace", INamespace.class);
				}
				 
			 });
	 
	 
	 //ICitation cache
	 private LoadingCache<Long, ICitation> citationCcahe = CacheBuilder.newBuilder()
			 .maximumSize(1000L)
			 .expireAfterAccess(240L, TimeUnit.MINUTES)
			
			 .build(new CacheLoader<Long,ICitation>() {
				@Override
				public ICitation load(Long key) throws Exception {
					return  ndexService._orientDbGraph.addVertex("class:citation", ICitation.class);
				}
				 
			 });
	 
	//IEdge cache
		 private LoadingCache<Long, IEdge> edgeCcahe = CacheBuilder.newBuilder()
				 .maximumSize(1000L)
				 .expireAfterAccess(240L, TimeUnit.MINUTES)
				
				 .build(new CacheLoader<Long,IEdge>() {
					@Override
					public IEdge load(Long key) throws Exception {
						return  ndexService._orientDbGraph.addVertex("class:edge", IEdge.class);
					}
					 
				 });
		 
		//INode cache
		 private LoadingCache<Long, INode> nodeCcahe = CacheBuilder.newBuilder()
				 .maximumSize(1000L)
				 .expireAfterAccess(240L, TimeUnit.MINUTES)
				
				 .build(new CacheLoader<Long,INode>() {
					@Override
					public INode load(Long key) throws Exception {
						return  ndexService._orientDbGraph.addVertex("class:node", INode.class);
					}
					 
				 });
		 
		//ISupport cache
		 private LoadingCache<Long, ISupport> supportCcahe = CacheBuilder.newBuilder()
				 .maximumSize(1000L)
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
	// find the jdexid from the identifier cache
	// find the INamespace from the vertex frame cache
	public INamespace findNamespaceByPrefix(String prefix) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(prefix), "A namespace prefix is required");
		Preconditions.checkArgument(!XbelCacheService.INSTANCE.isNovelIdentifier(prefix),
				"The prefix " +prefix +" is not registered");
		try {
			Long jdexId = XbelCacheService.INSTANCE.accessIdentifierCache().get(prefix);
			INamespace ns = this.namespaceCcahe.getIfPresent(jdexId);
			return ns;
		} catch (ExecutionException e) {
			
			e.printStackTrace();
		}
		return null;
	}


	@Override
	public IBaseTerm findOrCreateIBaseTerm(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return baseTermCcahe.get(jdexId);
		
	}


	@Override
	public IFunctionTerm findOrCreateIFunctionTerm(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return functionTermCcahe.get(jdexId);
	}


	@Override
	public INamespace findOrCreateINamespace(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return namespaceCcahe.get(jdexId);
	}


	@Override
	public ICitation findOrCreateICitation(Long jdexId)
			throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return citationCcahe.get(jdexId);
	}


	@Override
	public IEdge findOrCreateIEdge(Long jdexId) throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return edgeCcahe.get(jdexId);
	}


	@Override
	public INode findOrCreateINode(Long jdexId) throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return nodeCcahe.get(jdexId);
	}


	@Override
	public ISupport findOrCreateISupport(Long jdexId) throws ExecutionException {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		this.jdexIdSet.add(jdexId);
		return supportCcahe.get(jdexId);
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
	            final ORID userRid = RidConverter.convertToRid(newNetworkMembership.getResourceId());

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
		public INetwork getCurrentNetwork() {return this.network;}

	    /*
	     * find the ITerm (either Base or Function) by jdex id
	     */
		@Override
		public ITerm findChildITerm(Long jdexId) throws ExecutionException {
			Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
					"A valid JDExId is required");
			return Objects.firstNonNull((ITerm) this.baseTermCcahe.getIfPresent(jdexId),
					(ITerm) this.functionTermCcahe.getIfPresent(jdexId));
	
		}
	

	







}
