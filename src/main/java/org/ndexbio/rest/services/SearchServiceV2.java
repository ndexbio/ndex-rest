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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.http.client.ClientProtocolException;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.RequestDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.UserIndexManager;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.network.query.EdgeCollectionQuery;
import org.ndexbio.model.object.CXSimplePathQuery;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.SimpleQuery;
import org.ndexbio.model.object.SolrSearchResult;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.security.GoogleOpenIDAuthenticator;
import org.ndexbio.security.LDAPAuthenticator;
import org.ndexbio.security.OAuthTokenRenewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@Path("/v2/search")
public class SearchServiceV2 extends NdexService {
	
//	private static final String GOOGLE_OAUTH_FLAG = "USE_GOOGLE_AUTHENTICATION";
//	private static final String GOOGLE_OATH_KEY = "GOOGLE_OATH_KEY";
	
	
	static Logger logger = LoggerFactory.getLogger(BatchServiceV2.class);

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
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws SolrServerException 
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
			throws IllegalArgumentException, NdexException, SQLException, SolrServerException, IOException {

		logger.info("[start: Searching user \"{}\"]", simpleUserQuery.getSearchString());
		
		try (UserDAO dao = new UserDAO ()){

			final SolrSearchResult<User> users = dao.findUsers(simpleUserQuery, skipBlocks, blockSize);
			
			logger.info("[end: Returning {} users from search]", users.getNumFound());			
			return users;
		} 
		
	}

	
	/**************************************************************************
	 * Find Groups based on search parameters - string matching for now
	 * 
	 * @params searchParameters The search parameters.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return Groups that match the search criteria.
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws SolrServerException 
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
			throws IllegalArgumentException, NdexException, SQLException, SolrServerException, IOException {

		logger.info("[start: Search group \"{}\"]", simpleQuery.getSearchString());
		
		try (GroupDAO dao = new GroupDAO()) {
			final SolrSearchResult<Group> groups = dao.findGroups(simpleQuery, skipBlocks, blockSize);
			logger.info("[end: Search group \"{}\"]", simpleQuery.getSearchString());
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
		
		logger.info("[start: Retrieving NetworkSummary objects using query \"{}\"]", 
				query.getSearchString());		
		
    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSearchResult result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			logger.info("[end: Retrieved {} NetworkSummary objects]", result.getNetworks().size());		
			return result;

        } catch (Exception e) {
			logger.error("[end: Retrieving NetworkSummary objects using query \"{}\". Exception caught:]{}", 
					query.getSearchString(), e);	
			e.printStackTrace();
        	throw new NdexException(e.getMessage());
        }
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
		
		UUID networkId = UUID.fromString(networkIdStr);

		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
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
	public Response advanceQuery(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,			
			final EdgeCollectionQuery queryParameters
			) throws NdexException, SQLException   {
		
	/*	if ( networkIdStr.equals("0000"))
			return Response.ok().build();*/
		
		UUID networkId = UUID.fromString(networkIdStr);

		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
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
	
	
	
	@POST
	@PermitAll
	@Path("/network/genes")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public NetworkSearchResult searchNetworkByGenes(
			final List<String> query)
			throws IllegalArgumentException, NdexException {

		Set<String> r = expandGeneSearchTerms(query);
		StringBuilder lStr = new StringBuilder ();
		for ( String i : r)  lStr.append( i + " ");
		
		SimpleNetworkQuery finalQuery = new SimpleNetworkQuery();
		finalQuery.setSearchString(lStr.toString());
		
		try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSearchResult result = dao.findNetworks(finalQuery, 0, 1000, this.getLoggedInUser());
			return result;

        } catch (Exception e) {
        	throw new NdexException(e.getMessage());
        }
	}
	
	private Set<String> expandGeneSearchTerms(Collection<String> geneSearchTerms) throws NdexException  {
		
		Client client = ClientBuilder.newBuilder().build();
		
		MultivaluedMap<String, String> formData = new MultivaluedHashMap<String, String>();
		StringBuilder lStr = new StringBuilder ();
		
		for ( String i : geneSearchTerms)  lStr.append( i + ",");

	    formData.add("q", lStr.toString());
	    formData.add("scopes", "symbol,entrezgene,ensemblgene,alias,uniprot");
	    formData.add("fields", "symbol,name,taxid,entrezgene,ensembl.gene,alias,uniprot");
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
        Set<String> expendedTerms = new HashSet<> ();
        for ( Map<String,Object> termObj : expensionResult) {
        	Boolean notFound = (Boolean)termObj.get("notfound");
        	if ( notFound!=null && notFound.booleanValue()) {
        		missList.add((String)termObj.get("query"));
        		continue;
        	}
        		
        	String term = (String)termObj.get("ensembl.gene");
        	if (term !=null)
        		expendedTerms.add(term);
        	
        	term = (String)termObj.get("symbol");
        	if ( term !=null)
        		expendedTerms.add(term);
        	
        	Integer id = (Integer) termObj.get("entrezgene");
        	if ( id !=null)
        		expendedTerms.add(id.toString());
        	
        	List<String> aliases = (List<String>) termObj.get("alias");
        	if ( aliases !=null) {
        			expendedTerms.addAll(aliases);
        	}
        } 
        
        return expendedTerms;
        
	}

}
