package org.ndexbio.common.models.search;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileSearchResult;
import org.ndexbio.model.object.FileVisibilityType;
import org.ndexbio.model.object.SimpleFileQuery;
import org.ndexbio.common.solr.SolrClientWrapper;

/**
 *
 * @author churas
 */
public class SolrSearchProvider implements SearchProvider {

	SolrClientWrapper _client;
	
	public SolrSearchProvider(SolrClientWrapper client){
		this._client = client;
	}
	@Override
	public void close() throws Exception {
		return;
	}

	@Override
	public FileSearchResult searchFiles(SimpleFileQuery query, FileVisibilityType visibilityType, int skipBlocks, int blockSize) throws NdexException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
}
