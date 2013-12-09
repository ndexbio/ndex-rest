package org.ndexbio.xbel.cache;

import java.util.concurrent.TimeUnit;

import org.ndexbio.xbel.model.Namespace;
import org.ndexbio.xbel.service.JdexIdService;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/*
 * Service implement as a Singleton to provide access to cached XBEL 
 * model objects. 
 * LoadingCache objects are responsible for loading data from OrientDB database
 * upon cache misses
 */
public enum XbelCacheService {
	INSTANCE;
	
	 private LoadingCache<String, Long> identifierCache; 
	 /*
	  * the key for a BaseTern entry is ns:value
	  * the key for a FunctionTerm  entry is a concatenation of its children's id
	  * the value is the JdexId for the term itself
	  */
	 private LoadingCache<String, Long> termCache;
	 
	 
	 public boolean isNovelTerm(String aTermIdentifier){
		 if(Strings.isNullOrEmpty(aTermIdentifier)) { return false;}
		 if( null == this.accessTermCache().getIfPresent(aTermIdentifier)) {
			 return true;
		 }
		 return false;
	 }
	 /*
	  * public method to determine if a namespace has already been 
	  * registered in the cache
	  */
	 public boolean isNovelIdentifier(String identifier){
		 if(Strings.isNullOrEmpty(identifier)) { return false;}
		 if(null == this.accessIdentifierCache().getIfPresent(identifier)){
			 return true;
		 }
		 return false;
	 }
	 
	 
	 public LoadingCache<String,Long> accessTermCache(){
		 if (null == termCache){
			 this.initializeTermCache();
		 }
		 return termCache;
	 }
	 public LoadingCache<String,Long> accessIdentifierCache() {
		 if (null == identifierCache) {
			 initializeIdentifierCache();
		 }
		 return identifierCache;
	 }
	 
	 private void initializeIdentifierCache() {
		 this.identifierCache = CacheBuilder.newBuilder()
				 .maximumSize(1000L)
				 .expireAfterAccess(120L, TimeUnit.MINUTES)
				 .removalListener(new IdentifierRemovalListener())
				 .build(new CacheLoader<String,Long>() {

					@Override
					public Long load(String key) throws Exception {
						
						System.out.println("Cache created new jdex id for identifier: "
								+key);
						return JdexIdService.INSTANCE.getNextJdexId();
					}
					 
				 });
	 }
	 
	 
	 private void initializeTermCache() {
		 this.termCache = CacheBuilder.newBuilder()
				 .maximumSize(5000L)
				 .expireAfterAccess(60L, TimeUnit.MINUTES)
				 .removalListener(new TermRemovalListener()) 
				 .build(new CacheLoader<String,Long>() {
						@Override
						// the supplied key is not in the cache
						// generate a new JDEx ID for a new key value pair
						public Long load(String key) throws Exception {
							
							System.out.println("Cache created new jdex id for identifier: "
									+key);
							return JdexIdService.INSTANCE.getNextJdexId();
						}
						 
					 });
				 
	 }
	 
	 
}
