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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocumentList;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.errorcodes.NDExError;
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
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;

@Path("/v2/search")
public class SearchServiceV2 extends NdexService {
	
//	private static final String GOOGLE_OAUTH_FLAG = "USE_GOOGLE_AUTHENTICATION";
//	private static final String GOOGLE_OATH_KEY = "GOOGLE_OATH_KEY";
	
	
	//static Logger logger = LoggerFactory.getLogger(BatchServiceV2.class);
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
	@Operation(summary = "Search Users", description = "Returns a SearchResult object which contains an array of User objects and the total hit count of the search.")
	@Produces("application/json")
	
	public static SolrSearchResult<User> findUsers(
			SimpleQuery simpleUserQuery, 
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize
			)
			throws Exception {

		accLogger.info("[data]\t[query:" +simpleUserQuery.getSearchString() + "]" );
		
		try (UserDAO dao = new UserDAO ()){

			final SolrSearchResult<User> users = dao.findUsers(simpleUserQuery, skipBlocks, blockSize);
			
			return users;
		} 
		
	}

	
	/**************************************************************************
	 * Find Groups based on search parameters - string matching for now
	 * 
	 * @params searchParameters The search parameters.
	 * @return Groups that match the search criteria.
	 * @throws SQLException 
	 * @throws NdexException 
	 * @throws SolrServerException 
	 * @throws IOException 
	 **************************************************************************/
	@POST
	@PermitAll
	@AuthenticationNotRequired
	@Path("/group")
	@Operation(summary = "Search Groups", description = "Returns a SearchResult object which contains an array of Group objects and the total hit count of the search.")
	@Produces("application/json")
	public static SolrSearchResult<Group> findGroups(SimpleQuery simpleQuery,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize) throws SQLException, IOException, SolrServerException, NdexException  {

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
	@Operation(summary = "Search Networks", description = "Returns a SearchResult object which contains an array of NetworkSummary objects and total hit count of the search.")
	@Produces("application/json")
	// TODO: need to node accountName is a filter and you cannot use wildcards
	public NetworkSearchResult searchNetwork(
			final SimpleNetworkQuery query,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize)
			throws IllegalArgumentException, NdexException {
				
		accLogger.info("[data]\t[acc:"+ query.getAccountName() + "]\t[query:" +query.getSearchString() + "]" );
		
    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSearchResult result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			return result;

        } catch (NdexException e1) {
        		throw e1;
		} catch (Exception e) {
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
			//checkIfQueryIsAllowed(networkId, dao);
			getSolrIdxReady(networkId, dao);

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
	
	
	
	@SuppressWarnings("resource")
	@PermitAll
	@POST
	@Path("/network/{networkId}/query")
	@Operation(summary = "Query Network", description = "Returns a CX network that is a 'neighborhood' subnetwork of the network specified by networkid.")
	@Produces("application/json")

	public Response queryNetworkAsCX(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,
			@DefaultValue("false") @QueryParam("save") boolean saveAsNetwork,
			final CXSimplePathQuery queryParameters
			) throws NdexException, SQLException, URISyntaxException, SolrServerException, IOException   {
		
		accLogger.info("[data]\t[depth:"+ queryParameters.getSearchDepth() + "][query:" + queryParameters.getSearchString() + "]" );		
		
		if ( queryParameters.getSearchDepth() <1) {
			queryParameters.setSearchDepth(1);
		}
		UUID networkId = UUID.fromString(networkIdStr);

		UUID userId = getLoggedInUserId();
		if (  saveAsNetwork) {
			if (userId == null)
				throw new BadRequestException("Only authenticated users can save query results.");
			try (UserDAO dao = new UserDAO()) {
				   dao.checkDiskSpace(userId);
			}
		}
		
		String networkName;
		try (NetworkDAO dao = new NetworkDAO())  {
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
			networkName = dao.getNetworkName(networkId);
			getSolrIdxReady(networkId, dao);
			
		}   
/*		ProvenanceEntity ei = new ProvenanceEntity();
		ei.setUri(Configuration.getInstance().getHostURI()  + 
	            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ networkIdStr + "/summary" );
		ei.addProperty("dc:title", networkName); */
	
		if (networkName == null)
			networkName = "Neighborhood query result on unnamed network";
		else
			networkName = "Neighborhood query result on network - " + networkName;
		
		Client client = ClientBuilder.newBuilder().build();
		
		String prefix = Configuration.getInstance().getProperty("NeighborhoodQueryURL");
        WebTarget target = client.target(prefix + networkId + "/query");
        Response response = target.request().post(Entity.entity(queryParameters, "application/json"));
        
        if ( response.getStatus()!=200) {
        	NDExError obj = response.readEntity(NDExError.class);
        		throw new NdexException(obj.getMessage());
        }
        
		InputStream in = response.readEntity(InputStream.class);

        if (saveAsNetwork) {
        /*		ProvenanceEntity entity = new ProvenanceEntity();
        		ProvenanceEvent evt = new ProvenanceEvent("Neighborhood query",new Timestamp(Calendar.getInstance().getTimeInMillis()));
        		evt.addProperty("Query terms", queryParameters.getSearchString());
        		evt.addProperty("Query depth", String.valueOf(queryParameters.getSearchDepth()));
     		evt.addProperty( "user name", this.getLoggedInUser().getUserName());
        		evt.addInput(ei);
        		entity.setCreationEvent(evt); */
        		return saveQueryResult(networkName, userId, getLoggedInUser().getUserName(), in);
        }  
        
        	return Response.ok().entity(in).build();
        
	}

	public static void getSolrIdxReady(UUID networkId, NetworkDAO dao)
			throws SQLException, ObjectNotFoundException, SolrServerException, IOException, NdexException {
		int nodeCount = dao.getNodeCount(networkId);
		
		try (SingleNetworkSolrIdxManager solr = new SingleNetworkSolrIdxManager(networkId.toString())) {
			boolean ready = solr.isReady(nodeCount < SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD);
			if ( !ready ) {
				if (nodeCount < SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD) 
					throw new NdexException ("Failed to create Solr Index on this network.");
				throw new NdexException("NDEx server hasn't finished creating index on this network yet. Please try again later");
			}
		}
	}
	
	public static Response saveQueryResult(String networkName, UUID ownerUUID,String ownerName,
			InputStream in) throws SQLException, URISyntaxException {
		// create a network entry in db
		UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();

	    try (NetworkDAO dao = new NetworkDAO()) {
	    	   dao.CreateEmptyNetworkEntry(uuid, ownerUUID, ownerName, 0,networkName, null);
    // 	   dao.setProvenance(uuid, entity);
		   dao.commit();
	    }

	    // start the saving thread.
	    NetworkStreamSaverThread worker = new NetworkStreamSaverThread(uuid, in);
	    worker.start();
	    
	    // return the URL as resource
	    String urlStr = Configuration.getInstance().getHostURI()  + 
	            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ uuid;
		URI l = new URI (urlStr);
		return Response.created(l).entity(l).build();

	}
	
/*	private class  NetworkStreamSaverThread extends Thread 
	{
		UUID networkUUID;
		InputStream input;
		
		public NetworkStreamSaverThread(UUID networkId, InputStream in) {
			this.networkUUID = networkId;
			this.input = in;
		//	this.owner = ownerName;
		}
		
		@Override
		public void run() {
			String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID.toString();

			// Create dir
			java.nio.file.Path dir = Paths.get(pathPrefix);
			Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
			FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);

			try {
				Files.createDirectory(dir, attr);

				// write content to file
				File cxFile = new File(pathPrefix + "/network.cx");
				java.nio.file.Files.copy(input, cxFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

				long fileSize = cxFile.length();
				try (NetworkDAO dao = new NetworkDAO()) {
					dao.setNetworkFileSize(networkUUID, fileSize);
					dao.commit();
				} catch (SQLException | NdexException e2) {
					e2.printStackTrace();
					try (NetworkDAO dao = new NetworkDAO()) {
						dao.setErrorMessage(networkUUID, "Failed to set network file size: " + e2.getMessage());
						dao.unlockNetwork(networkUUID);
					} catch (SQLException e3) {
						e3.printStackTrace();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				try (NetworkDAO dao = new NetworkDAO()) {
					dao.setErrorMessage(networkUUID, "Failed to create network file on the server: " + e.getMessage());
					dao.unlockNetwork(networkUUID);
				} catch (SQLException e2) {
					e2.printStackTrace();
				}

			}

			IOUtils.closeQuietly(input);
		      
			try (NetworkDAO dao = new NetworkDAO ()) {
				try ( CXNetworkLoader loader = new CXNetworkLoader(networkUUID, false,dao, VisibilityType.PRIVATE, null, 5000) ) {
							loader.persistCXNetwork();
				} catch ( IOException | NdexException | SQLException | RuntimeException | SolrServerException e1) {
					e1.printStackTrace();
					try {
						dao.setErrorMessage(networkUUID, e1.getMessage());
						dao.setFlag(networkUUID, "readonly", false);	 
						try {
							dao.updateNetworkVisibility(networkUUID, VisibilityType.PRIVATE, true);
						} catch (NdexException e) {
							System.out.print("Error when updating network visibility: " + e.getMessage());
							e.printStackTrace();
						}
						dao.unlockNetwork(networkUUID);
					} catch (SQLException e) {
						System.out.println("Failed to set Error for network " + networkUUID);
						e.printStackTrace();
					}
					
				} 
			} catch (SQLException e) {
					e.printStackTrace();
			}			
			
		}
	} */

	@PermitAll
	@POST
	@Path("/network/{networkId}/interconnectquery")
	@Operation(summary = "Interconnect Query", description = "Returns a CX network that is a 'neighborhood' subnetwork where all the paths must start and end at one of the query nodes in the network specified by networkid.")
	@Produces("application/json")

	public Response interconnectQuery(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,
			@DefaultValue("false") @QueryParam("save") boolean saveAsNetwork,
			final CXSimplePathQuery queryParameters
			) throws NdexException, SQLException, URISyntaxException, SolrServerException, IOException   {
		
		accLogger.info("[data]\t[depth:"+ queryParameters.getSearchDepth() + "][query:" + queryParameters.getSearchString() + "]" );		
		
		if ( queryParameters.getSearchDepth() <1) {
			queryParameters.setSearchDepth(1);
		}

		UUID networkId = UUID.fromString(networkIdStr);

		UUID userId = getLoggedInUserId();
		if (  saveAsNetwork) {
			if (userId == null)
				throw new BadRequestException("Only authenticated users can save query results.");
			try (UserDAO dao = new UserDAO()) {
				   dao.checkDiskSpace(userId);
			}
		}
		
		String networkName;

		try (NetworkDAO dao = new NetworkDAO())  {
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
			getSolrIdxReady(networkId, dao);
			networkName = dao.getNetworkName(networkId);
			
		}   
		
/*		ProvenanceEntity ei = new ProvenanceEntity();
		ei.setUri(Configuration.getInstance().getHostURI()  + 
	            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ networkIdStr + "/summary" );
		ei.addProperty("dc:title", networkName); */
		
		if (networkName == null)
			networkName = "Interconnect query result on unnamed network";
		else
			networkName = "Interconnect query result on network - " + networkName;
				
		Client client = ClientBuilder.newBuilder().build();
		
		/*Map<String, Object> queryEntity = new TreeMap<>();
		queryEntity.put("terms", queryParameters.getSearchString());
		queryEntity.put("searchDepth", queryParameters.getSearchDepth());
		queryEntity.put("edgeLimit", queryParameters.getEdgeLimit());
		queryEntity */
		String prefix = Configuration.getInstance().getProperty("NeighborhoodQueryURL");
        WebTarget target = client.target(prefix + networkId + "/interconnectquery");
        Response response = target.request().post(Entity.entity(queryParameters, "application/json"));
        
        if ( response.getStatus()!=200) {
        		NDExError obj = response.readEntity(NDExError.class);
        		throw new NdexException(obj.getMessage());
        }
        
      //     String value = response.readEntity(String.class);
       //    response.close();  
        InputStream in = response.readEntity(InputStream.class);
        if (saveAsNetwork) {
    	/*		ProvenanceEntity entity = new ProvenanceEntity();
        		ProvenanceEvent evt = new ProvenanceEvent("Interconnect query",new Timestamp(Calendar.getInstance().getTimeInMillis()));
        		evt.addProperty("Query terms", queryParameters.getSearchString());
     		evt.addProperty( "user name", this.getLoggedInUser().getUserName());
        		evt.addInput(ei);
        		entity.setCreationEvent(evt); */
    			return saveQueryResult(networkName, userId, getLoggedInUser().getUserName(), in);
        }  
        return Response.ok().entity(in).build();
		
	}
	
	@PermitAll
	@POST
	@Path("/network/{networkId}/advancedquery")
	@Operation(summary = "Advanced Query", description = "This method retrieves a filtered subnetwork of the network specified by ‘networkId’ based on a POSTed JSON query object.")
	@Produces("application/json")
   
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
	@Operation(summary = "Search Networks by Gene/Protein", description = "Returns a SearchResult object which contains an array of NetworkSummary objects and total hit count of the search.")
	@Produces("application/json")
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
	
	private static Set<String> expandGeneSearchTerms(Collection<String> geneSearchTerms) throws NdexException  {
		
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
	    formData.add("species", "9606");
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
        	
        	String id =  (String)termObj.get("entrezgene");
        	if ( id !=null)
        		expendedTerms.add(id);
        	
        	addTermsToExpensionSet(termObj.get("uniprot.Swiss-Prot"), expendedTerms);
        	/*String term = (String) termObj.get("uniprot.Swiss-Prot");
        	if ( term !=null)
        		expendedTerms.add(term);*/
        	
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
	
	private static void addTermsToExpensionSet(Object term, Set<String> expendedTerms) {
		if ( term !=null) {
    		if (term instanceof String) {
    			expendedTerms.add((String)term);
    		} else {
            	expendedTerms.addAll((List<String>) term);
    		}
    	}
	}

	private static void addSingleTermToExpensionSet(Object term, Set<String> expendedTerms) {
		if ( term !=null) {
    		if (term instanceof String) {
    			expendedTerms.add((String)term);
    		} else {
            	expendedTerms.add(term.toString());
    		}
    	}
	}
}
