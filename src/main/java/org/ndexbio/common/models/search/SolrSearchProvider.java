package org.ndexbio.common.models.search;

import java.io.IOException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.ndexbio.common.solr.PrivateNFSIndexManager;
import org.ndexbio.common.solr.PublicNFSIndexManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileSearchResult;
import org.ndexbio.model.object.FileVisibilityType;
import org.ndexbio.model.object.SimpleFileQuery;
import org.ndexbio.common.solr.SolrClientWrapper;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.VisibilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides methods query Solr
 * 
 * @author churas
 */
public class SolrSearchProvider implements SearchProvider {

	private static final Logger _logger = LoggerFactory.getLogger(SolrSearchProvider.class.getName());
	private SolrClientWrapper _client;
	private int _defaultMaxSearchResultRows;
	
	/**
	 * Constructor
	 * 
	 * @param client 
	 */
	public SolrSearchProvider(SolrClientWrapper client, int defaultMaxSearchResultRows){
		this._client = client;
		this._defaultMaxSearchResultRows = defaultMaxSearchResultRows;
	}
	
	/**
	 * 
	 * @throws Exception 
	 */
	@Override
	public void close() throws Exception {
		if (_client != null){
			_client.close();
		}
	}

	private SolrQuery getSolrQuery(SimpleFileQuery query, int skipBlocks, int blockSize){
		SolrQuery solrQuery = new SolrQuery();
		if ( skipBlocks >=0)
		  solrQuery.setStart(skipBlocks);
		if ( blockSize >0 )
			solrQuery.setRows(blockSize);
		else 
			solrQuery.setRows(_defaultMaxSearchResultRows);
		return solrQuery;
	}
	
	@Override
	public FileSearchResult searchFiles(SimpleFileQuery query, VisibilityType visibilityType, User owner,
										int skipBlocks, int blockSize) throws NdexException {
		
		// default to public core/index
		String coreName = PublicNFSIndexManager.CORE_NAME;
       
		// Determine which database to hit based on visibilityType.
		// If private go to private-nfs and if public use public-nfs
		if (visibilityType != null && visibilityType == VisibilityType.PRIVATE){
			coreName = PrivateNFSIndexManager.CORE_NAME;
		}
		
		SolrQuery solrQuery = getSolrQuery(query, skipBlocks, blockSize);
		FileSearchResult result = new FileSearchResult();
		try {
			// send query to _client.
			
			QueryResponse qr = _client.query(coreName, solrQuery);
			SolrDocumentList  dds = qr.getResults();
			for (SolrDocument doc : dds){
				String uuid = (String)doc.get(PublicNFSIndexManager.UUID);
				String entityType = (String)doc.get(PublicNFSIndexManager.ENTITY_TYPE);
				
				// @TODO: 
				//        Use appropriate DAO based on entityType to get object content from uuid
				//        and convert to FileItemSummary and add to result object
			
			}
		} catch(IOException io){
			_logger.error("Caught IOException performing search: " + io.getMessage(), io);
			throw new NdexException("Caught IOException performing search", io);
		} catch(SolrServerException sse){
			_logger.error("Caught SolrServerException performing search: " + sse.getMessage(), sse);
			throw new NdexException("Caught SolrServerException performing search", sse);
		}
		return result;
	}
}
