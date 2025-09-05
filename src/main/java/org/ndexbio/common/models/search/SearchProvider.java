package org.ndexbio.common.models.search;

import org.ndexbio.common.solr.SolrClientWrapper;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileSearchResult;
import org.ndexbio.model.object.FileVisibilityType;
import org.ndexbio.model.object.SimpleFileQuery;


/**
 * Defines methods for searching for entities in NDEx
 * 
 * @author churas
 */
public interface SearchProvider extends AutoCloseable {
	
	/**
	 * File search
	 * 
	 * @param query Search query
	 * @param visibilityType Denotes whether the search is for private or public data
	 * @param skipBlocks Search offset
	 * @param blockSize Number of results to return
	 * @return Result of search
	 * @throws NdexException If there was an error running the search
	 */
	FileSearchResult searchFiles(SimpleFileQuery query,
			FileVisibilityType visibilityType,
			int skipBlocks, int blockSize) throws NdexException;
}
