package org.ndexbio.common.solr;


import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;



/**
 *
 * @author churas
 */
public class SolrClientWrapperImplTest {
	
	@Ignore
	public void testCreateCoreAndPerformSomeIndexes() throws Exception {
		SolrClientWrapperImpl client = new SolrClientWrapperImpl("http://localhost:8983/solr");
		
		client.createCoreIfNeeded("private-nfs");
		client.dropCore("private-nfs");
		client.createCoreIfNeeded("private-nfs");

		// lets try
		Map<String, Object> fieldAttributes = new LinkedHashMap<>();
        fieldAttributes.put("name", "uuid");
        fieldAttributes.put("type", "string");
        fieldAttributes.put("stored", false);
        fieldAttributes.put("indexed", true);
		
		SolrInputDocument doc = new SolrInputDocument();
		doc.addField("uuid", "12345678910");
		LinkedList<SolrInputDocument> docs = new LinkedList<>();
		docs.add(doc);
		client.commit("private-nfs", docs);
		
		SolrQuery solrQuery = new SolrQuery("*:*");
		solrQuery.setRows(100000); // Set a large number for all documents
		QueryResponse rsp = client.query("private-nfs", solrQuery);
		SolrDocumentList sdl = rsp.getResults();
		for(SolrDocument s : sdl){
			assertEquals(s.toString(), "hi");
		}
	}
	
	
	
}
