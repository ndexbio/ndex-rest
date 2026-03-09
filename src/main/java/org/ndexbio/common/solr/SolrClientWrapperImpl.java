package org.ndexbio.common.solr;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps common Solr client operations
 * 
 * @author churas
 */
public class SolrClientWrapperImpl implements SolrClientWrapper {
	
	/**
	 * Base client for solr, needed for core operations
	 */
	private SolrClient _baseClient;
	private final Map<String, SolrClient> _clientCache = new ConcurrentHashMap<>();

	/**
	 * Factory to get solrclient and coreadmin objects
	 * to communicate with Solr
	 */
	private SolrObjectFactory _factory;
	
	private static final Logger logger = LoggerFactory.getLogger(SolrClientWrapperImpl.class.getName());

	/**
	 * Constructor
	 * 
	 * @param factory Factory to get SolrClient and CoreAdmin objects
	 */
	public SolrClientWrapperImpl(final SolrObjectFactory factory){
		_baseClient = factory.getSolrClient(null);
		_factory = factory;
	}


	/**
	 * Converts Solr exception to NdexException
	 */
	protected static NdexException convertException(BaseHttpSolrClient.RemoteSolrException e, String core_name) {
		if (e.code() == 400) {
			String err = e.getMessage();
			Pattern p = Pattern.compile("Error from server at .*/" + core_name +": (.*)");
			Matcher m = p.matcher(e.getMessage());
			if ( m.matches()) {
				err = m.group(1);
			} 
			return new BadRequestException(err);
		}	
		return new NdexException("Error from NDEx Solr server: " + e.getMessage());
	}
	
	/**
	 * Creates core on Solr if needed. If core exists, nothing is done
	 * 
	 * @param coreName Name of core
	 * @throws SolrServerException
	 * @throws IOException
	 * @throws NdexException 
	 */
	@Override
	public void createCoreIfNeeded(final String coreName) throws SolrServerException, IOException, NdexException {

		CoreAdminResponse foo = _factory.getCoreAdminRequestGetStatus(coreName);
		if (foo.getStatus() != 0 ) {
			throw new NdexException ("Failed to get status of solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
		}
		NamedList<Object> bar = foo.getResponse();
		
		NamedList<Object> st = (NamedList<Object>)bar.get("status");
		
		NamedList<Object> core = (NamedList<Object>)st.get(coreName);
		if ( core.size() == 0 ) {
			logger.debug("Solr core " + coreName + " doesn't exist. Creating it now ....");

			CoreAdminRequest.Create creator = _factory.getCoreAdminRequestCreate();
			creator.setCoreName(coreName);
			creator.setConfigSet(coreName); 
			foo = creator.process(_baseClient);				
			if ( foo.getStatus() != 0 ) {
				throw new NdexException ("Failed to create solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
			}
			logger.debug("Done.");		
		}
		else {
			logger.debug("Found core "+ coreName + " in Solr.");	
		}

	}

	/**
	 * Calls _factory.getCoreAdminRequestUnloadCore to drop or unload a core
	 * 
	 * @param coreName name of core
	 * @throws IOException
	 * @throws SolrServerException
	 * @throws NdexException 
	 */
	@Override
	public void dropCore(final String coreName) throws IOException, SolrServerException, NdexException {
		try {
			_factory.getCoreAdminRequestUnloadCore(coreName, true, true);
		} catch (HttpSolrClient.RemoteSolrException e4) {
			logger.error(e4.code() + " - " + e4.getMessage(), e4);
			if ( e4.getMessage().indexOf("Cannot unload non-existent core") == -1) {
				throw new NdexException("Unexpected Solr Exception: " + e4.getMessage());
			}	
		} 
	}

	/**
	 * Calls solrclient.commit(false, true, true) adding any documents found in documents
	 * passed in
	 * 
	 * @param coreName Core to commit
	 * @param documents documents to add
	 * @throws SolrServerException
	 * @throws IOException 
	 */
	@Override
	public void commit(final String coreName, final Collection<SolrInputDocument> documents) throws SolrServerException, IOException {

		var client = getSolrClient(coreName);
		if (documents != null && documents.isEmpty() == false){
			var ur = client.add(documents);
			if (ur != null && logger.isDebugEnabled()){
				logger.debug("add: " + ur.toString());
			}
		}
		
		var ur = client.commit(false, true, true);
		if (ur != null && logger.isDebugEnabled()){
			logger.debug("commit response: " + ur.toString());
		}
	}

	/**
	 * Deletes index of document
	 * 
	 * @param coreName Core where document resides
	 * @param id uuid of document
	 * @param commit whether to tell solr to commit the change or not
	 * @throws SolrServerException
	 * @throws IOException 
	 */
	@Override
	public void delete(final String coreName, final String id, boolean commit) throws SolrServerException, IOException {
		var client = getSolrClient(coreName);
		var ur = client.deleteById(id);
		if (ur != null && logger.isDebugEnabled()){
			logger.debug("deleteById response: " + ur.toString());
		}
		if (commit == true){
			ur = client.commit(false, true, true);
			if (ur != null && logger.isDebugEnabled()){
				logger.debug("commit response: " + ur.toString());
			}
		}
	}

	/**
	 * Queries Solr 
	 * 
	 * @param coreName Core to query
	 * @param query The query
	 * @return Response from Solr
	 * @throws IOException
	 * @throws SolrServerException
	 * @throws NdexException Converted BaseHttpSolrClient.RemoteSolrException
	 */
	@Override
	public QueryResponse query(final String coreName, final SolrQuery query) throws IOException, SolrServerException, NdexException {
		
		try {
			return _baseClient.query(coreName, query, SolrRequest.METHOD.POST);		
			
		} catch (BaseHttpSolrClient.RemoteSolrException e) {
			throw convertException(e, coreName);
		}
	}
	public SolrClient getSolrClient(final String coreName) {
		String key = (coreName == null || coreName.trim().isEmpty()) ? "" : coreName;
		return _clientCache.computeIfAbsent(key, k -> {
			if (k.isEmpty()) {
				return _baseClient;
			}
			return _factory.getSolrClient(coreName);
		});
	}

	/**
	 * Closes base client
	 */
	@Override
	public void close() {
		try {
			if (_baseClient != null){
				_baseClient.close();
			}
			for (SolrClient client : _clientCache.values()) {
				try { client.close(); } catch (Exception ignored) {}
			}
			_clientCache.clear();
		} catch (IOException e) {
			logger.info("Caught exception closing client", e);
		}
	}
}
