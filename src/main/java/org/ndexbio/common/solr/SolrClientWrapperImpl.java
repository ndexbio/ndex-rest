package org.ndexbio.common.solr;

import java.io.IOException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.solr.client.solrj.SolrQuery;
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
 *
 * @author churas
 */
public class SolrClientWrapperImpl implements SolrClientWrapper {
	
	private final HttpSolrClient client;
	private final String solrUrl;
	private static final Logger logger = LoggerFactory.getLogger(SolrClientWrapperImpl.class.getName());
	
	public SolrClientWrapperImpl(final String solrURL){
		solrUrl = solrURL;
		client = new HttpSolrClient.Builder(solrUrl).build();
	}

	private void setBaseURL(final String coreName){
		client.setBaseURL(solrUrl + "/" + coreName);
	}
	
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
	
	@Override
	public void createCoreIfNeeded(final String coreName) throws SolrServerException, IOException, NdexException {
		CoreAdminResponse foo = CoreAdminRequest.getStatus(coreName,client);	
		if (foo.getStatus() != 0 ) {
			throw new NdexException ("Failed to get status of solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
		}
		NamedList<Object> bar = foo.getResponse();
		
		NamedList<Object> st = (NamedList<Object>)bar.get("status");
		
		NamedList<Object> core = (NamedList<Object>)st.get(coreName);
		if ( core.size() == 0 ) {
			logger.debug("Solr core " + coreName + " doesn't exist. Creating it now ....");

			CoreAdminRequest.Create creator = new CoreAdminRequest.Create(); 
			creator.setCoreName(coreName);
			creator.setConfigSet( coreName); 
			foo = creator.process(client);				
			if ( foo.getStatus() != 0 ) {
				throw new NdexException ("Failed to create solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
			}
			logger.debug("Done.");		
		}
		else {
			logger.debug("Found core "+ coreName + " in Solr.");	
		}

	}

	@Override
	public void dropCore(final String coreName) throws IOException, SolrServerException, NdexException {
		setBaseURL(coreName);
		try {
			CoreAdminRequest.unloadCore(coreName, true, true, client);
		} catch (HttpSolrClient.RemoteSolrException e4) {
			logger.error(e4.code() + " - " + e4.getMessage(), e4);
			if ( e4.getMessage().indexOf("Cannot unload non-existent core") == -1) {
				throw new NdexException("Unexpected Solr Exception: " + e4.getMessage());
			}	
		} 
	}

	@Override
	public void commit(final String coreName, final Collection<SolrInputDocument> documents) throws SolrServerException, IOException {
		setBaseURL(coreName);
		if (documents != null && documents.isEmpty() == false){
			client.add(documents);
		}
		client.commit(false, true, true);
	}

	@Override
	public void delete(final String coreName, final String id, boolean commit) throws SolrServerException, IOException {
		setBaseURL(coreName);
		client.deleteById(id);
		if (commit == true){
			client.commit(false, true, true);
		}
	}

	@Override
	public QueryResponse query(final String coreName, final SolrQuery query) throws NdexException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void close() {
		try {
			client.close();
		} catch (IOException e) {
			logger.info("Caught exception closing client", e);
		}
	}
	
}
