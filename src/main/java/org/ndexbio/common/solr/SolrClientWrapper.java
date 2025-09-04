package org.ndexbio.common.solr;

import java.io.IOException;
import java.util.Collection;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.ndexbio.model.exceptions.NdexException;

/**
 * Provides higher level access to SOLR functionality
 * 
 * @author churas
 */
public interface SolrClientWrapper extends AutoCloseable {
	
	/**
	 * Creates core in Solr if it does not already exist
	 * @param coreName Name of core
	 * @throws NdexException 
	 */
	void createCoreIfNeeded(final String coreName) throws SolrServerException, IOException, NdexException;
	
	/**
	 * Removes core from Solr completely (deletes filesystem data)
	 * where a core contains one or more indexes
	 * 
	 * @param coreName Name of core
	 * @throws IOException
	 * @throws SolrServerException
	 * @throws NdexException 
	 */
	void dropCore(final String coreName) throws IOException, SolrServerException, NdexException;

	/**
	 * Commits documents. If documents is null or empty then commit all pending
	 * data
	 * @param coreName Name of core
	 * @param documents Document to commit
	 * @throws SolrServerException
	 * @throws IOException 
	 */
	void commit(final String coreName, Collection<SolrInputDocument> documents) throws SolrServerException, IOException;

	/**
	 * Delete index of object from core 
	 * @param coreName Name of core
	 * @param id id of object in index to delete
	 * @throws SolrServerException
	 * @throws IOException 
	 */
	void delete(final String coreName, final String id, boolean commit) throws SolrServerException, IOException;
	
	/**
	 * Query Solr
	 * @param coreName
	 * @param query
	 * @return
	 * @throws NdexException 
	 */
	QueryResponse query(final String coreName, final SolrQuery query) throws IOException, SolrServerException, NdexException;
	
	/**
	 * Close connection to Solr client
	 */
	@Override
	void close();
}
