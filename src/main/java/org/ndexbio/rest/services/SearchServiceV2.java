/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.services;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocumentList;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.network.query.EdgeCollectionQuery;
import org.ndexbio.model.object.CXSimplePathQuery;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.SimpleQuery;
import org.ndexbio.model.object.SolrSearchResult;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/v2/search")
public class SearchServiceV2 extends NdexService {
	
//	private static final String GOOGLE_OAUTH_FLAG = "USE_GOOGLE_AUTHENTICATION";
//	private static final String GOOGLE_OATH_KEY = "GOOGLE_OATH_KEY";
	
	
	static Logger logger = LoggerFactory.getLogger(BatchServiceV2.class);
	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	static final int networkQuerySizeLimit = 500000;

	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public SearchServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	
	
	/**************************************************************************
	 * Finds users based on the search parameters.
	 * 
	 * @param searchParameters
	 *            The search parameters.
	 * @throws Exception 
	 **************************************************************************/
	@POST
	@PermitAll
	@AuthenticationNotRequired
	@Path("/user")
	@Produces("application/json")
	@ApiDoc("Returns a list of users based on the range [skipBlocks, blockSize] and the POST data searchParameters. "
			+ "The searchParameters must contain a 'searchString' parameter. ")
	public SolrSearchResult<User> findUsers(
			SimpleQuery simpleUserQuery, 
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize
			)
			throws Exception {

	//	logger.info("[start: Searching user \"{}\"]", simpleUserQuery.getSearchString());
		accLogger.info("[data]\t[query:" +simpleUserQuery.getSearchString() + "]" );
		
		try (UserDAO dao = new UserDAO ()){

			final SolrSearchResult<User> users = dao.findUsers(simpleUserQuery, skipBlocks, blockSize);
			
	//		logger.info("[end: Returning {} users from search]", users.getNumFound());			
			return users;
		} 
		
	}

	
	/**************************************************************************
	 * Find Groups based on search parameters - string matching for now
	 * 
	 * @params searchParameters The search parameters.
	 * @return Groups that match the search criteria.
	 * @throws Exception 
	 **************************************************************************/
	@POST
	@PermitAll
	@AuthenticationNotRequired
	@Path("/group")
	@Produces("application/json")
	@ApiDoc("Returns a list of groups found based on the searchOperator and the POSTed searchParameters.")
	public SolrSearchResult<Group> findGroups(SimpleQuery simpleQuery,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize)
			throws Exception {

	//	logger.info("[start: Search group \"{}\"]", simpleQuery.getSearchString());
		accLogger.info("[data]\t[query:" +simpleQuery.getSearchString() + "]" );

		try (GroupDAO dao = new GroupDAO()) {
			final SolrSearchResult<Group> groups = dao.findGroups(simpleQuery, skipBlocks, blockSize);
	//		logger.info("[end: Search group \"{}\"]", simpleQuery.getSearchString());
			return groups;
		} 
	}
	
	

	@POST
	@PermitAll
	@Path("/network")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public NetworkSearchResult searchNetwork(
			final SimpleNetworkQuery query,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize)
			throws IllegalArgumentException, NdexException {
		
		//logger.info("[start: Retrieving NetworkSummary objects using query \"{}\"]", 
		//		query.getSearchString());		
		
		accLogger.info("[data]\t[acc:"+ query.getAccountName() + "]\t[query:" +query.getSearchString() + "]" );
		
    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSearchResult result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
	//		logger.info("[end: Retrieved {} NetworkSummary objects]", result.getNetworks().size());		
			return result;

        } catch (Exception e) {
		//	logger.error("[end: Retrieving NetworkSummary objects using query \"{}\". Exception caught:]{}", 
		//			query.getSearchString(), e);	
			e.printStackTrace();
        	throw new NdexException(e.getMessage());
        }
	}

	@PermitAll
	@POST
	@Path("/network/{networkId}/nodes")
	@Produces("application/json")
   
	public SolrDocumentList queryNetworkNodes(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,
			@DefaultValue("100") @QueryParam("limit") int limit,			
			final SimpleQuery queryParameters
			) throws NdexException, SQLException, SolrServerException, IOException   {
		
		accLogger.info("[data]\t[query:" + queryParameters.getSearchString() + "]" );		
		
		UUID networkId = UUID.fromString(networkIdStr);

		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
			checkIfQueryIsAllowed(networkId, dao);
		}   
		
		try (SingleNetworkSolrIdxManager solr = new SingleNetworkSolrIdxManager(networkId.toString())) {
			SolrDocumentList r = solr.getNodeIdsByQuery(queryParameters.getSearchString(), limit);
			return r;
		}
	/*	Client client = ClientBuilder.newBuilder().build();
		
		Map<String, Object> queryEntity = new TreeMap<>();
		queryEntity.put("terms", queryParameters.getSearchString());
		queryEntity.put("depth", queryParameters.getSearchDepth());
		queryEntity.put("edgeLimit", queryParameters.getEdgeLimit());
		String prefix = Configuration.getInstance().getProperty("NeighborhoodQueryURL");
        WebTarget target = client.target(prefix + networkId + "/query");
        Response response = target.request().post(Entity.entity(queryEntity, "application/json"));
        
        if ( response.getStatus()!=200) {
        	Object obj = response.readEntity(Object.class);
        	throw new NdexException(obj.toString());
        }
        
      //     String value = response.readEntity(String.class);
       //    response.close();  
        InputStream in = response.readEntity(InputStream.class);
 
        return Response.ok().entity(in).build(); */
		
	}
	
	
	
	@PermitAll
	@POST
	@Path("/network/{networkId}/query")
	@Produces("application/json")
    @ApiDoc("Retrieves a 'neighborhood' subnetwork of the network specified by ‘networkId’. The query finds " +
            "the subnetwork by a traversal of the network starting with nodes associated with identifiers " +
            "specified in a POSTed JSON query object. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#queryNetwork\">here</a>.")
	public Response queryNetworkAsCX(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,
			final CXSimplePathQuery queryParameters
			) throws NdexException, SQLException   {
		
		accLogger.info("[data]\t[depth:"+ queryParameters.getSearchDepth() + "][query:" + queryParameters.getSearchString() + "]" );		
		
		if ( queryParameters.getSearchDepth() <1) {
			throw new BadRequestException("Query depth should be a positive integer.");
		}
		UUID networkId = UUID.fromString(networkIdStr);

		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
			checkIfQueryIsAllowed(networkId, dao);
		}   
		
		Client client = ClientBuilder.newBuilder().build();
		
		Map<String, Object> queryEntity = new TreeMap<>();
		queryEntity.put("terms", queryParameters.getSearchString());
		queryEntity.put("depth", queryParameters.getSearchDepth());
		queryEntity.put("edgeLimit", queryParameters.getEdgeLimit());
		String prefix = Configuration.getInstance().getProperty("NeighborhoodQueryURL");
        WebTarget target = client.target(prefix + networkId + "/query");
        Response response = target.request().post(Entity.entity(queryEntity, "application/json"));
        
        if ( response.getStatus()!=200) {
        	Object obj = response.readEntity(Object.class);
        	throw new NdexException(obj.toString());
        }
        
      //     String value = response.readEntity(String.class);
       //    response.close();  
        InputStream in = response.readEntity(InputStream.class);
 
        return Response.ok().entity(in).build();
		
	}

	
	@PermitAll
	@POST
	@Path("/network/{networkId}/advancedquery")
	@Produces("application/json")
    @ApiDoc("Retrieves a subnetwork of the network specified by ‘networkId’. The query finds " +
            "the subnetwork by a filtering the network on conditions defined in filter query object. " +
            "specified in a POSTed JSON query object. " )
	public Response advancedQuery(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,			
			final EdgeCollectionQuery queryParameters
			) throws NdexException, SQLException   {
		
	/*	if ( networkIdStr.equals("0000"))
			return Response.ok().build();*/
		ObjectMapper mapper = new ObjectMapper();
		try {
			accLogger.info("[data]\t[query:" + mapper.writeValueAsString(queryParameters)+ "]" );
		} catch (JsonProcessingException ee) {
			logger.info("Failed to generate json string for logging in function SearchServiceV2.advancedQuery:" + ee.getMessage());
		}

		UUID networkId = UUID.fromString(networkIdStr);

		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
			checkIfQueryIsAllowed(networkId, dao);
		}   
		
		Client client = ClientBuilder.newBuilder().build();
		
		/*Map<String, Object> queryEntity = new TreeMap<>();
		queryEntity.put("terms", queryParameters.getSearchString());
		queryEntity.put("depth", queryParameters.getSearchDepth());
		queryEntity.put("edgeLimit", queryParameters.getEdgeLimit()); */
		String prefix = Configuration.getInstance().getProperty("AdvancedQueryURL");
        WebTarget target = client.target(prefix + networkId + "/advancedquery");
        Response response = target.request().post(Entity.entity(queryParameters, "application/json"));
        
        if ( response.getStatus()!=200) {
        	Object obj = response.readEntity(Object.class);
        	throw new NdexException(obj.toString());
        }
        
      //     String value = response.readEntity(String.class);
       //    response.close();  
        InputStream in = response.readEntity(InputStream.class);
 
        return Response.ok().entity(in).build();
		
	}
	
	
	private static void checkIfQueryIsAllowed(UUID networkId, NetworkDAO dao) throws ForbiddenOperationException, ObjectNotFoundException, SQLException {
		
		if ( dao.getNetworkEdgeCount(networkId) > networkQuerySizeLimit)
			throw new ForbiddenOperationException("Query on networks that have over " + networkQuerySizeLimit + " edges is not supported in this release. "
					+ "You can download the network and process it on your own computer. Please email support@ndexbio.org if you need further assistance.");
	}
	
	
	@POST
	@PermitAll
	@Path("/network/genes")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public NetworkSearchResult searchNetworkByGenes(
			final SimpleQuery geneQuery,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize)
			throws IllegalArgumentException, NdexException {

		accLogger.info("[data]\t[query:" +geneQuery.getSearchString() + "]" );
		
		if ( geneQuery.getSearchString().trim().length() == 0 || geneQuery.getSearchString().trim().equals("*")) {
			try (NetworkDAO dao = new NetworkDAO()) {
				SimpleNetworkQuery finalQuery = new SimpleNetworkQuery();
				finalQuery.setSearchString(geneQuery.getSearchString());
				NetworkSearchResult result = dao.findNetworks(finalQuery, skipBlocks, blockSize, this.getLoggedInUser());
				return result;

	        } catch (Exception e) {
	        	throw new NdexException(e.getMessage());
	        }
			
		}

        String[] query = geneQuery.getSearchString().split("(,|\\s)+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
		Set<String> processedTerms = new HashSet<>(query.length);
		for ( String q : query) {
			if ( q.startsWith("\"") && q.endsWith("\"") || q.startsWith("\'") && q.endsWith("\'") )
				processedTerms.add(q.substring(1, q.length()-1));
			else 
				processedTerms.add(q);
		}
			
		if ( processedTerms.size() == 0)
			return new NetworkSearchResult();
		
		Set<String> r = expandGeneSearchTerms(processedTerms);
		StringBuilder lStr = new StringBuilder ();
		for ( String os : processedTerms) 
			lStr.append("\"" + os + "\" ");
		for ( String i : r) {
			if (! processedTerms.contains(i))
				lStr.append( "\"" + i + "\" ");
			//else 
			//	System.out.println("term " + i + " is in query, ignoring it.");
		}
		SimpleNetworkQuery finalQuery = new SimpleNetworkQuery();
		finalQuery.setSearchString(lStr.toString());
		logger.info("Final search string is ("+ lStr.length()+"): " + lStr.toString());

		try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSearchResult result = dao.findNetworks(finalQuery, skipBlocks, blockSize, this.getLoggedInUser());
			return result;

        } catch (Exception e) {
        	throw new NdexException(e.getMessage());
        }
	}
	
	private Set<String> expandGeneSearchTerms(Collection<String> geneSearchTerms) throws NdexException  {
		
		Client client = ClientBuilder.newBuilder().build();
        Set<String> expendedTerms = new HashSet<> ();
		
		MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
		StringBuilder lStr = new StringBuilder ();
		
		for ( String i : geneSearchTerms) 
			if ( i.trim().length() != 0 ) 
				lStr.append( i + ",");

		if (lStr.length()<1)
			return expendedTerms;
		
		String q = lStr.substring(0,lStr.length()-1);
	    formData.add("q", q);
	    formData.add("scopes", "symbol,entrezgene,ensemblgene,alias,uniprot");
	    formData.add("fields", "symbol,name,taxid,entrezgene,ensembl.gene,alias,uniprot,MGI,RGD,HGNC");
	    formData.add("dotfield", "true");

	//    lStr.append("&scope=symbol,entrezgene,ensemblgene,alias,uniprot&fields=symbol,name,taxid,entrezgene,ensembl.gene,alias,uniprot&dotfield=true");
		
        WebTarget target = client.target("http://mygene.info/v3/query");
        Response response = target.request().post(Entity.form(formData));
        
        if ( response.getStatus()!=200) {
        	Object obj = response.readEntity(Object.class);
        	throw new NdexException(obj.toString());
        }
        
    //    Object expensionResult = response.readEntity(Object.class);
   //     System.out.println(expensionResult);
        List<Map<String,Object>> expensionResult = response.readEntity(new GenericType<List<Map<String,Object>>>() {});
        Set<String> missList = new HashSet<> ();
        for ( Map<String,Object> termObj : expensionResult) {
        	Boolean notFound = (Boolean)termObj.get("notfound");
        	if ( notFound!=null && notFound.booleanValue()) {
        		missList.add((String)termObj.get("query"));
        		continue;
        	}
        		
        	addTermsToExpensionSet(termObj.get("ensembl.gene"), expendedTerms);
        	addTermsToExpensionSet(termObj.get("symbol"), expendedTerms);
        	
        	Integer id = (Integer) termObj.get("entrezgene");
        	if ( id !=null)
        		expendedTerms.add(id.toString());
        	
        	String term = (String) termObj.get("uniprot.Swiss-Prot");
        	if ( term !=null)
        		expendedTerms.add(term);
        	
        	addTermsToExpensionSet (termObj.get("alias"), expendedTerms);
        	addTermsToExpensionSet (termObj.get("uniprot.TrEMBL"), expendedTerms);
        	
        	addSingleTermToExpensionSet (termObj.get("entrezgene"), expendedTerms);

        	addSingleTermToExpensionSet (termObj.get("name"), expendedTerms);
        	
        	String hgnc = (String)termObj.get("HGNC");
        	if ( hgnc !=null)
        		expendedTerms.add("hgnc:" + hgnc);
        	
     //   	addSingleTermToExpensionSet (termObj.get("MGI"), expendedTerms);

        } 
        
        return expendedTerms;
        
	}
	
	private void addTermsToExpensionSet(Object term, Set<String> expendedTerms) {
		if ( term !=null) {
    		if (term instanceof String) {
    			expendedTerms.add((String)term);
    		} else {
            	expendedTerms.addAll((List<String>) term);
    		}
    	}
	}

	private void addSingleTermToExpensionSet(Object term, Set<String> expendedTerms) {
		if ( term !=null) {
    		if (term instanceof String) {
    			expendedTerms.add((String)term);
    		} else {
            	expendedTerms.add(term.toString());
    		}
    	}
	}
}
