package org.ndexbio.orientdb.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.ITerm;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.primitives.Longs;
import com.tinkerpop.frames.VertexFrame;

/*
 * Singleton that provides temporary persistence of an NDEx network
 * while the network is under construction
 * Utilizes a Google Guava cache that uses Jdexids as keys and VertexFram implementations 
 * as values
 */

public enum NDExMemoryPersistence implements NDExPersistenceService {
	
	INSTANCE;
	 private LoadingCache<Long, VertexFrame> vertexFrameCache; 
	 
	 
	 
	 private LoadingCache<Long,VertexFrame> accessVertexFrameCache() {
		 if (null == vertexFrameCache) {
			 initializeVertexFrameCache();
		 }
		 return vertexFrameCache;
	 }
	 
	 private void initializeVertexFrameCache() {
		 this.vertexFrameCache = CacheBuilder.newBuilder()
				 .maximumSize(1000L)
				 .expireAfterAccess(240L, TimeUnit.MINUTES)
				
				 .build(new CacheLoader<Long,VertexFrame>() {
					@Override
					public VertexFrame load(Long key) throws Exception {
						// TODO replace default construct with database findOrCreate method
						return null;
					}
					 
				 });
	 }

	@Override
	public boolean isEntityPersisted(Long jdexId) {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDExId is required");
		return (null != this.accessVertexFrameCache().getIfPresent(jdexId));
		
	}
	
	@Override
	public ITerm findOrCreateNdexEntity(ITerm iterm) {
		Preconditions.checkArgument(null != iterm, "An ITerm implementation is required");
		Preconditions.checkArgument(null != Longs.tryParse(iterm.getJdexId()),
				"A valid JDEx ID is required");
		if (this.isEntityPersisted(Long.getLong(iterm.getJdexId()))){
			try {
				return (ITerm) this.accessVertexFrameCache().get(Longs.tryParse(iterm.getJdexId()));
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			}
			
		} else {
			this.accessVertexFrameCache().put(Longs.tryParse(iterm.getJdexId()), iterm);
			return iterm;
		}
		
		return null;
	}

	@Override
	public VertexFrame findNdexEntityByJdexId(Long jdexId) {
		Preconditions.checkArgument(null != jdexId && jdexId.longValue() >0,
				"A valid JDEx ID is required");
		
		try {
			return this.accessVertexFrameCache().get(jdexId);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
	}

	@Override
	public ICitation findOrCreateNdexEntity(ICitation citation) {
		Preconditions.checkArgument(null != citation, "An ICitation implementation is required");
		Preconditions.checkArgument(null != Longs.tryParse(citation.getJdexId()),
				"A valid JDEx ID is required");
		if (this.isEntityPersisted(Long.getLong(citation.getJdexId()))){
			try {
				return (ICitation) this.accessVertexFrameCache()
						.get(Longs.tryParse(citation.getJdexId()));
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			}
			
		} else {
			this.accessVertexFrameCache().put(Longs.tryParse(citation.getJdexId()), citation);
			return citation;
		}
		
		return null;
	}

	@Override
	public IEdge findOrCreateNdexEntity(IEdge edge) {
		Preconditions.checkArgument(null != edge, "An IEdge implementation is required");
		Preconditions.checkArgument(null != Longs.tryParse(edge.getJdexId()),
				"A valid JDEx ID is required");
		if (this.isEntityPersisted(Long.getLong(edge.getJdexId()))){
			try {
				return (IEdge) this.accessVertexFrameCache().get(Longs.tryParse(edge.getJdexId()));
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			}
			
		} else {
			this.accessVertexFrameCache().put(Longs.tryParse(edge.getJdexId()), edge);
			return edge;
		}
		
		return null;
	}

	@Override
	public INamespace findOrCreateNdexEntity(INamespace namespace) {
		Preconditions.checkArgument(null != namespace, "An INamespace implementation is required");
		Preconditions.checkArgument(null != Longs.tryParse(namespace.getJdexId()),
				"A valid JDEx ID is required");
		if (this.isEntityPersisted(Long.getLong(namespace.getJdexId()))){
			try {
				return (INamespace) this.accessVertexFrameCache()
						.get(Longs.tryParse(namespace.getJdexId()));
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			}
			
		} else {
			this.accessVertexFrameCache().put(Longs.tryParse(namespace.getJdexId()), namespace);
			return namespace;
		}
		
		return null;
	}

	@Override
	public INode findOrCreateNdexEntity(INode node) {
		Preconditions.checkArgument(null != node, "An INode implementation is required");
		Preconditions.checkArgument(null != Longs.tryParse(node.getJdexId()),
				"A valid JDEx ID is required");
		if (this.isEntityPersisted(Long.getLong(node.getJdexId()))){
			try {
				return (INode) this.vertexFrameCache.get(Longs.tryParse(node.getJdexId()));
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			}
			
		} else {
			this.vertexFrameCache.put(Longs.tryParse(node.getJdexId()), node);
			return node;
		}
		
		return null;
	}

	@Override
	public ISupport findOrCreateNdexEntity(ISupport support) {
		Preconditions.checkArgument(null != support, "An ISupport implementation is required");
		Preconditions.checkArgument(null != Longs.tryParse(support.getJdexId()),
				"A valid JDEx ID is required");
		if (this.isEntityPersisted(Long.getLong(support.getJdexId()))){
			try {
				return (ISupport) this.vertexFrameCache.get(Longs.tryParse(support.getJdexId()));
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				
			}
			
		} else {
			this.vertexFrameCache.put(Longs.tryParse(support.getJdexId()), support);
			return support;
		}
		
		return null;
	}

}
