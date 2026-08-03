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
	 * Creates core in Solr if it does not already exist, using a named configSet.
	 *
	 * <p>Use this when the configSet is shared rather than named after the core, as the
	 * per-network node cores are: they all share the {@code ndex-nodes} configSet.
	 *
	 * @param coreName      Name of core
	 * @param configSetName Name of the configSet the core should use
	 * @throws NdexException
	 */
	void createCoreIfNeeded(final String coreName, final String configSetName) throws SolrServerException, IOException, NdexException;

	/**
	 * Reports whether a core is registered in Solr.
	 *
	 * @param coreName Name of core
	 * @return true when Solr knows about the core
	 * @throws NdexException
	 */
	boolean coreExists(final String coreName) throws SolrServerException, IOException, NdexException;

	/**
	 * Creates a core, failing if it already exists.
	 *
	 * <p>Unlike {@link #createCoreIfNeeded(String, String)} this does not swallow an
	 * "already exists" response - callers that treat a pre-existing core as a distinct
	 * outcome rely on seeing that error.
	 *
	 * @param coreName      Name of core to create
	 * @param configSetName configSet the core should use
	 * @throws NdexException
	 */
	void createCore(final String coreName, final String configSetName) throws SolrServerException, IOException, NdexException;

	/**
	 * Creates a configSet by cloning a template configSet.
	 *
	 * @param configSetName     Name of the configSet to create
	 * @param baseConfigSetName Name of the template to clone from
	 * @throws NdexException
	 */
	void createConfigSet(final String configSetName, final String baseConfigSetName) throws SolrServerException, IOException, NdexException;

	/**
	 * Adds documents to a core without committing. Use for batching a large document set;
	 * follow the final batch with a commit.
	 *
	 * @param coreName  Name of core
	 * @param documents Documents to add; null or empty is a no-op
	 * @throws SolrServerException
	 * @throws IOException
	 */
	void add(final String coreName, Collection<SolrInputDocument> documents) throws SolrServerException, IOException;

	/**
	 * Commits documents, choosing between a soft and a hard commit.
	 *
	 * <p>A hard commit flushes to disk, so it is what index <em>creation</em> should use - a
	 * soft-committed index is visible but not durable until a later autoCommit fires.
	 *
	 * @param coreName    Name of core
	 * @param documents   Documents to add before committing; null or empty just commits
	 * @param hardCommit  true for a hard (durable) commit, false for a soft commit
	 * @throws SolrServerException
	 * @throws IOException
	 */
	void commit(final String coreName, Collection<SolrInputDocument> documents, boolean hardCommit) throws SolrServerException, IOException;

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
