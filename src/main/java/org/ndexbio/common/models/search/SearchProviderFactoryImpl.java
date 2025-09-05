package org.ndexbio.common.models.search;

import org.ndexbio.common.solr.SolrClientWrapperImpl;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.common.solr.SolrObjectFactory;

/**
 * Factory to get SearchProvider object
 * 
 * @author churas
 */
public class SearchProviderFactoryImpl implements SearchProviderFactory {

	private SolrObjectFactory _factory;
	private int _defaultMaxSearchResultRows;
	
	/**
	 * Constructor
	 * 
	 * @param factory Factory to needed obtain SolrClient and CoreAdmin objects
	 */
	public SearchProviderFactoryImpl(SolrObjectFactory factory, int defaultMaxSearchResultRows){
		_factory = factory;
		_defaultMaxSearchResultRows = defaultMaxSearchResultRows;
	}
	
	/**
	 * Generates a new SearchProvider with SolrClientWrapperImpl providing
	 * Solr interface
	 * 
	 * @return Search provider
	 * @throws NdexException 
	 */
	@Override
	public SearchProvider getSearchProvider() throws NdexException {
		SolrClientWrapperImpl wrapper = new SolrClientWrapperImpl(_factory);
		return new SolrSearchProvider(wrapper, _defaultMaxSearchResultRows);
	}
}
