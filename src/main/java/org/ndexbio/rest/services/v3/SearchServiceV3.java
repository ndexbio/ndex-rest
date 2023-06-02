package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.model.errorcodes.NDExError;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.network.query.FilterCriterion;
import org.ndexbio.model.object.CXSimplePathQuery;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.rest.services.SearchServiceV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Path("/v3/search")

public class SearchServiceV3 extends NdexService  {

	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	public SearchServiceV3(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@PermitAll
	@POST
	@Path("/networks/{networkId}/edges")
	//@Produces("application/json")
	
	public Response getEdges(	@PathParam("networkId") final String networkId,
				@DefaultValue("-1") @QueryParam("size") int limit,
				@DefaultValue("desc") @QueryParam("order") String order,
				@DefaultValue("cx2")  @QueryParam("format") String format,
				@QueryParam("accesskey") String accessKey,
				final FilterCriterion queryParameter) throws SQLException, NdexException, JsonParseException, JsonMappingException, IOException
	{
				
		UUID networkUUID = UUID.fromString(networkId);
	    	
		try (NetworkDAO dao = new NetworkDAO()) {
			if ( !dao.isReadable(networkUUID, getLoggedInUserId()) && 
    				! dao.accessKeyIsValid(networkUUID, accessKey)) {
				throw new UnauthorizedOperationException("User doesn't have access to this network.");
			}
					
			EdgeFilter filter = new EdgeFilter(networkId, queryParameter, limit, order);
			Set<CxEdge> result = filter.filterTopN();
			if (format.equals("cx2"))
				return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(result).build();
			else if ( format.equals("cx")) {
				String aspectFile = Configuration.getInstance().getNdexRoot() + File.separator + "data" +File.separator + networkId
								+ File.separator + CX2NetworkLoader.cx2AspectDirName+  File.separator + CxAttributeDeclaration.ASPECT_NAME;
				File vsFile = new File( aspectFile );
				ObjectMapper om = new ObjectMapper();
				CxAttributeDeclaration[] ds = om.readValue(vsFile, CxAttributeDeclaration[].class); 
						
				Map<String, DeclarationEntry> edgeAttrDecls= 
						ds[0].getDeclarations().get(CxEdge.ASPECT_NAME);
				return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(
								cvtToCXlist(result, edgeAttrDecls)).build();

			} else 
				throw new NdexException ("Invalid value for parameter 'format'.");
			} 
	}
		
	private static List<Map<String,Object>> cvtToCXlist(Set<CxEdge> cx2Set, Map<String, DeclarationEntry> edgeAttrDecls) {
		List<Map<String,Object>> result = new ArrayList<>(cx2Set.size());
			
		for ( CxEdge e : cx2Set) {
			HashMap<String, Object> m = new HashMap<>(2);
			m.put("e", e.getCX1Edge(edgeAttrDecls));
			List<EdgeAttributesElement> attrs = e.getAttributesAsCX1List(edgeAttrDecls);
			if ( !attrs.isEmpty())
				m.put("a", attrs);
			result.add(m);
		}
		return result;
	}

	
	@PermitAll
	@POST
	@Path("/networks/{networkId}/query")
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
			SearchServiceV2.getSolrIdxReady(networkId, dao);
			
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
        WebTarget target = client.target(prefix + networkId + "/query?outputCX2=true");
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
        		return SearchServiceV2.saveQueryResult(networkName, userId, getLoggedInUser().getUserName(), in);
        }  
        
        	return Response.ok().entity(in).build();
        
	}
	
	@PermitAll
	@POST
	@Path("/networks/{networkId}/interconnectquery")
	@Produces("application/json")

	public Response interconnectQuery(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,
			@DefaultValue("false") @QueryParam("save") boolean saveAsNetwork,
			final CXSimplePathQuery queryParameters
			) throws NdexException, SQLException, URISyntaxException, SolrServerException, IOException   {
		
		if ( saveAsNetwork )
			throw new NdexException("Saving CX2 result is not implemented yet.");
		
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
			SearchServiceV2.getSolrIdxReady(networkId, dao);
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
        WebTarget target = client.target(prefix + networkId + "/interconnectquery?outputCX2=true");
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
    			return SearchServiceV2.saveQueryResult(networkName, userId, getLoggedInUser().getUserName(), in);
        }  
        return Response.ok().entity(in).build();
		
	}

}
