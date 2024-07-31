package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.cxio.util.CxConstants;
import org.ndexbio.model.errorcodes.NDExError;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.network.query.CXObjectFilter;
import org.ndexbio.model.network.query.FilterCriterion;
import org.ndexbio.model.network.query.FilteredDirectQuery;
import org.ndexbio.model.object.CXSimplePathQuery;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.tools.EdgeFilter;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.rest.services.SearchServiceV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Path("/v3/search")

public class SearchServiceV3 extends NdexService  {

	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	static final HashMap<String,Object> EMPTYOBJECT = new HashMap<>();
	
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
			
			String cx2AspectDir = Configuration.getInstance().getNdexRoot() + File.separator + "data" +File.separator + networkId
					+ File.separator + CX2NetworkLoader.cx2AspectDirName+  File.separator;
					
			EdgeFilter filter = new EdgeFilter(queryParameter, limit, order, cx2AspectDir);
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
			@DefaultValue("false") @QueryParam("preserveCoordinates") boolean preserveNodeCoordinates,			
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
		String param = preserveNodeCoordinates ? "&perserveCoordinates=true" : "";
        WebTarget target = client.target(prefix + networkId + "/query?outputCX2=true" + param);
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
			@DefaultValue("false") @QueryParam("preserveCoordinates") boolean preserveNodeCoordinates,
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
		String param = preserveNodeCoordinates ? "&perserveCoordinates=true" : "";
        WebTarget target = client.target(prefix + networkId + "/interconnectquery?outputCX2=true" + param);
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
	
	
	@PermitAll
	@POST
	@Path("/networks/{networkId}/nodes")
	@Produces("application/json")

	
	public Response getNodeAttributes (@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,
			final CXObjectFilter filterObject) throws SQLException, IOException, NdexException {
		
		if(filterObject.getAttributeNames().size()==0) {
			throw new BadRequestException("At least one attribute name is reqired in the 'attributeNames' field.");
		}
		
		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			UUID networkId = UUID.fromString(networkIdStr);
			if ( dao.isReadable(networkId, userId) || dao.accessKeyIsValid(networkId, accessKey)) {
				List<CxMetadata> md = dao.getCx2MetaDataList(networkId);
			
				String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkIdStr + "/aspects_cx2/";
			
				// get attribute declaration
				CxAttributeDeclaration decl= null;
				if (!md.stream().anyMatch(m -> m.getName().equals(CxNode.ASPECT_NAME))) 
					return Response.ok().type(MediaType.APPLICATION_JSON_TYPE)
							.entity(CxConstants.EMPTYOBJECT).build();

			
				if (md.stream().anyMatch(m -> m.getName().equals(CxAttributeDeclaration.ASPECT_NAME))) {
					try (AspectIterator<CxAttributeDeclaration> ei = new AspectIterator<>( pathPrefix + CxAttributeDeclaration.ASPECT_NAME, CxAttributeDeclaration.class)) {
						while (ei.hasNext()) {
							decl = ei.next();
							break;
						}
					}
				}
				if ( decl == null) {
					decl = new CxAttributeDeclaration();
				}	
				
				//check if the attributes exists
				Map<String, DeclarationEntry> nodeAttrDecl = decl.getAttributesInAspect(CxNode.ASPECT_NAME);
				if ( nodeAttrDecl == null)
					throw new ObjectNotFoundException("This network has no attributes on nodes.");
				
				for (String attrName : filterObject.getAttributeNames()) {
					if (nodeAttrDecl.get(attrName)==null)
						throw new ObjectNotFoundException("Node attribute '"+attrName+"' is not found in this network.");
				}
				
				PipedInputStream in = new PipedInputStream();
				 
				PipedOutputStream out;

				try {
					out = new PipedOutputStream(in);
				} catch (IOException e) {
					in.close();
					throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
				}
				
				new NodeAttrsFilterThread(out,filterObject, nodeAttrDecl, pathPrefix).start();
				return Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
			
			}
			
			throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
		}
	}
	
	protected class NodeAttrsFilterThread extends Thread {
		
		private PipedOutputStream out;
		private Set<Long> ids;
		private String pathPrefix;
		private Set<String> attrNames;
		private Map<String, DeclarationEntry> decls;
		
		
		public NodeAttrsFilterThread(PipedOutputStream out, 
				CXObjectFilter filterObject, Map<String, DeclarationEntry> nodeAttrDecl,
				String pathPrefix) {
			this.out = out;
			ids = filterObject.getIds();
			this.attrNames = filterObject.getAttributeNames();
			this.pathPrefix= pathPrefix;
			this.decls = nodeAttrDecl;

		}
		
		@Override
		public void run() {
			
			int count = 0;

			JsonFactory factory = new JsonFactory();
		    JsonGenerator generator;
			try {
				generator = factory.createGenerator(out);
		        generator.writeStartObject(); // Start the object
		        generator.setCodec(new ObjectMapper());
		        
		        //iterate throw the node aspect and return the filtered result
		        try (AspectIterator<CxNode> ei = new AspectIterator<>( pathPrefix + CxNode.ASPECT_NAME, CxNode.class)) {
		        	while (ei.hasNext()) {
		        		if ( count == ids.size())
		        			break;
					
		        		CxNode n = ei.next();
		        		if ( ids.contains(n.getId())) {
		        			Map<String,Object> attrs = new HashMap<>();
		        			for (String attrName : attrNames) {
		        				attrs.put(attrName, n.getWelldoneAttributeValue(attrName, decls.get(attrName)));
		        			}		        	        
		        	        generator.writeFieldName(n.getId().toString());
		    		        generator.writeObject(attrs);
		        	        count ++;
		        		}
		        	}
		            generator.writeEndObject(); // End the object
		            generator.close();
		        } catch (JsonProcessingException e) {
		        	// TODO Auto-generated catch block
		        	e.printStackTrace();
		        } catch (NdexException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {		
				try {
					out.flush();
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
/*	
	@PermitAll
	@POST
	@Path("/networks/{networkId}/filteredDirectQuery")
	@Produces("application/json")
	
	public Response filteredDirectQuery(
			@PathParam("networkId") final String networkIdStr,
			@QueryParam("accesskey") String accessKey,
			@DefaultValue("false") @QueryParam("preserveCoordinates") boolean preserveNodeCoordinates,
			final FilteredDirectQuery queryObject
			) throws NdexException, SQLException, URISyntaxException, SolrServerException, IOException   {
		

		UUID networkId = UUID.fromString(networkIdStr);

		UUID userId = getLoggedInUserId();
		
		String networkName;

		try (NetworkDAO dao = new NetworkDAO())  {
			if ( !dao.isReadable(networkId, userId) && !dao.accessKeyIsValid(networkId, accessKey)) {
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}
//			SearchServiceV2.getSolrIdxReady(networkId, dao);
			//TODO: get the modification time.
			networkName = dao.getNetworkName(networkId);
			
			
		}   
	
		Client client = ClientBuilder.newBuilder().build();
		
		
		String prefix = Configuration.getInstance().getProperty("NeighborhoodQueryURL");
		String param = preserveNodeCoordinates ? "&perserveCoordinates=true" : "";
        WebTarget target = client.target(prefix + networkId + "/interconnectquery?outputCX2=true" + param);
        Response response = target.request().post(Entity.entity(queryParameters, "application/json"));
        
        if ( response.getStatus()!=200) {
        		NDExError obj = response.readEntity(NDExError.class);
        		throw new NdexException(obj.getMessage());
        }
        
      //     String value = response.readEntity(String.class);
       //    response.close();  
        InputStream in = response.readEntity(InputStream.class);
        if (saveAsNetwork) {
  
    			return SearchServiceV2.saveQueryResult(networkName, userId, getLoggedInUser().getUserName(), in);
        }  
        return Response.ok().entity(in).build();
		
	}
*/
}
