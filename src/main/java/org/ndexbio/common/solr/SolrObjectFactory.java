package org.ndexbio.common.solr;

import java.io.IOException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;

/**
 * Defines interface for classes that create SolrClient and various CoreAdmin
 * objects used to interact with Solr the solr service
 * 
 * @author churas
 */
public interface SolrObjectFactory {
	
	/**
	 * Gets the shared SolrClient, bound to the Solr base URL. Pass the core name on each request.
	 * Callers never close it.
	 * @return the same client on every call
	 */
	public SolrClient getSolrClient();
	
	/**
	 * Gets status of a Core from Solr
	 * @param coreName Name of core to query
	 * @return
	 * @throws IOException
	 * @throws SolrServerException 
	 */
	public CoreAdminResponse getCoreAdminRequestGetStatus(final String coreName) throws IOException, SolrServerException;
	
	/**
	 * Creates CoreAdminRequest.Create object
	 * @return
	 */
	public CoreAdminRequest.Create getCoreAdminRequestCreate();

	/**
	 * Creates ConfigSetAdminRequest.Create object, used when a core needs its own
	 * configSet cloned from a template rather than sharing a static one.
	 * @return
	 */
	public ConfigSetAdminRequest.Create getConfigSetAdminRequestCreate();

	
	/**
	 * Removes core from Solr
	 * @param coreName Name of core to remove
	 * @param deleteIndex denotes whether to delete index
	 * @param deleteInstanceDir denotes whether to delete instance directory
	 * @return
	 * @throws IOException
	 * @throws SolrServerException 
	 */
	public CoreAdminResponse getCoreAdminRequestUnloadCore(String coreName, boolean deleteIndex, boolean deleteInstanceDir) throws IOException, SolrServerException;
	
	public GlobalNetworkIndexManager getGlobalNetworkIndexManager();
	public FolderIndexManager getFolderIndexManager();
	public ShortcutIndexManager getShortcutIndexManager();

	/**
	 * Gets a manager for one network's node query index, wired with the shared Solr client
	 * and a CX2 aspect reader.
	 *
	 * @param networkId network UUID, which is also its Solr core name
	 */
	public SingleNetworkSolrIdxManager getSingleNetworkSolrIdxManager(final String networkId);

	public UserIndexManager getUserIndexManager();

	/**
	 * Closes the shared Solr client. Only the owner of the process lifecycle calls this, once,
	 * at shutdown.
	 * @throws IOException
	 */
	public void close() throws IOException;

}
