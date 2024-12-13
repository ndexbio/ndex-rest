package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.io.FileUtils;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.CxAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cx2.io.CX2AspectWriter;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.network.NetworkSummaryV3;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.NetworkSummaryFormat;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.helpers.AmazonSESMailSender;
import org.ndexbio.rest.helpers.EZIDClient;
import org.ndexbio.rest.helpers.Security;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.task.CX2NetworkLoadingTask;
import org.ndexbio.task.NdexServerQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Path("/v3/networks")
public class NetworkServiceV3  extends NdexService {
	protected static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	static private final String readOnlyParameter = "readOnly";

	public NetworkServiceV3(@Context HttpServletRequest httpRequest
			) {
		super(httpRequest);
	}

	
	@PermitAll
	@GET
	@Path("/{networkid}")
	@Operation(summary = "Get Network in CX2 format", description = "Returns the specified network in CX2 format. This is performed as a monolithic operation, so it is typically advisable for applications to first use the getNetworkSummary method to check the node and edge counts for a network before retrieving the network.")

	public Response getCX2Network(	@PathParam("networkid") final String networkId,
			@QueryParam("download") boolean isDownload,
			@QueryParam("accesskey") String accessKey,
			@QueryParam("id_token") String id_token,
			@QueryParam("auth_token") String auth_token)
			throws Exception {
    	
    	String title = null;
    	try (NetworkDAO dao = new NetworkDAO()) {
    		UUID networkUUID = UUID.fromString(networkId);
    		if ( !dao.hasCX2(networkUUID)) {
    			throw new ObjectNotFoundException("CX2 network is not available for this network. ");
    		}
     		UUID userId = getLoggedInUserId();
    		if ( userId == null ) {
    			if ( auth_token != null) {
    				userId = getUserIdFromBasicAuthString(auth_token);
    			} else if ( id_token !=null) {
    				if ( getOAuthAuthenticator() == null)
    					throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
    				userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
    			}
    		}
    		if ( ! dao.isReadable(networkUUID, userId) && (!dao.accessKeyIsValid(networkUUID, accessKey))) 
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");
    		
    		title = dao.getNetworkName(networkUUID);
    	}
  
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/"+ CX2NetworkLoader.cx2NetworkFileName;

    	try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
		//	setZipFlag();
			ResponseBuilder r = Response.ok();
			if (isDownload) {
				if (title == null || title.length() < 1) {
					title = networkId;
				}
				title.replace('"', '_');
				r.header("Content-Disposition", "attachment; filename=\"" + title + ".cx2\"");
				r.header("Access-Control-Expose-Headers", "Content-Disposition");
			}
			return 	r.type(isDownload ? MediaType.APPLICATION_OCTET_STREAM_TYPE : MediaType.APPLICATION_JSON_TYPE)
					.entity(in).build();
		} catch (IOException e) {
			throw new NdexException ("Ndex server IO error: " + e.getMessage());
		}
		
	}  
	
	
	
	@PermitAll
	@GET
	@Path("/{networkid}/aspects")
	@Produces("application/json")

	public List<CxMetadata>  getCX2Metadata(	@PathParam("networkid") final String networkId,
			@QueryParam("accesskey") String accessKey)
			throws Exception {

    	UUID networkUUID = UUID.fromString(networkId);

		try (NetworkDAO dao = new NetworkDAO() ) {
			if ( dao.isReadable(networkUUID, getLoggedInUserId()) || dao.accessKeyIsValid(networkUUID, accessKey)) {
				List<CxMetadata> mdc = dao.getCx2MetaDataList(networkUUID);
		    	return mdc;
		}
			throw new UnauthorizedOperationException("User doesn't have access to this network.");
		}
	}
	
	@PermitAll
	@GET
	@Path("/{networkid}/aspects/edges") 
	public Response getEdges (
			@PathParam("networkid") final String networkId,
			@DefaultValue("first") @QueryParam("method") String method,
			@DefaultValue("-1") @QueryParam("size") int limit,
			@QueryParam("accesskey") String accessKey ) throws NdexException, SQLException {
		if ( method.equalsIgnoreCase("first"))
			return getAspectElements(networkId, CxEdge.ASPECT_NAME, limit, accessKey);
		else if ( !method.equalsIgnoreCase("random"))
			throw new NdexException ("Method " + method + " is not supported in this function.");
		else {
			if ( limit <=0)
				throw new NdexException ("size parameter has to be greater than 0 when getting random edges.");
			
			UUID networkUUID = UUID.fromString(networkId);
	    	
	    	try (NetworkDAO dao = new NetworkDAO()) {
	    		if ( !dao.isReadable(networkUUID, getLoggedInUserId()) && 
	    				! dao.accessKeyIsValid(networkUUID, accessKey)) {
	    			throw new UnauthorizedOperationException("User doesn't have access to this network.");
	    		}
	    		long edgeCount = dao.getNetworkEdgeCount(networkUUID);
	    		TreeSet<Long> positions = Util.generateRandomId(limit, edgeCount);
	    		
	    		File cx2AspectDir = new File (Configuration.getInstance().getNdexRoot() + "/data/" + networkId 
	    				+ "/" + CX2NetworkLoader.cx2AspectDirName);

	    		FileInputStream in = null;
				try {
					in = new FileInputStream(cx2AspectDir + "/" + CxEdge.ASPECT_NAME);
					   
					PipedInputStream pin = new PipedInputStream();
					PipedOutputStream out;
							
					try {
						out = new PipedOutputStream(pin);
					} catch (IOException e) {
						try {
							pin.close();
							in.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
					}
							
						new CX2RandomEdgeWriterThread(out,in, positions).start();
					//	logger.info("[end: Return get one aspect in network {}]", networkId);
						return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(pin).build();
					
				} catch (@SuppressWarnings("unused") FileNotFoundException e) {
						throw new ObjectNotFoundException("Aspect edges is not found in this network.");
				}
	    	}	
		}
	}

	
	@PermitAll
	@GET
	@Path("/{networkid}/aspects/{aspectname}")
	public Response getAspectElements(	@PathParam("networkid") final String networkId,
			@PathParam("aspectname") final String aspectName,
			@DefaultValue("-1") @QueryParam("size") int limit,
			@QueryParam("accesskey") String accessKey ) throws SQLException, NdexException
		 {

    	UUID networkUUID = UUID.fromString(networkId);
    	
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( !dao.isReadable(networkUUID, getLoggedInUserId()) && 
    				! dao.accessKeyIsValid(networkUUID, accessKey)) {
    			throw new UnauthorizedOperationException("User doesn't have access to this network.");
    		}
    		
    		File cx2AspectDir = new File (Configuration.getInstance().getNdexRoot() + "/data/" + networkId 
    				+ "/" + CX2NetworkLoader.cx2AspectDirName);
    		
			FileInputStream in = null;
			try {
				in = new FileInputStream(cx2AspectDir + "/" + aspectName);
				if ( limit <= 0) {
						return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
			    } 
				   
				PipedInputStream pin = new PipedInputStream();
				PipedOutputStream out;
						
				try {
					out = new PipedOutputStream(pin);
				} catch (IOException e) {
					try {
						pin.close();
						in.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
				}
						
					new CX2AspectElementsWriterThread(out,in, aspectName, limit).start();
				//	logger.info("[end: Return get one aspect in network {}]", networkId);
					return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(pin).build();
				
			} catch (FileNotFoundException e) {
					throw new ObjectNotFoundException("Aspect "+ aspectName + " is not found in this network.");
			}
		
    	}

	}  

	private class CX2AspectElementsWriterThread extends Thread {
		private OutputStream o;
	//	private String networkId;
		private FileInputStream in;
		private String aspect;
		private int limit;
		public CX2AspectElementsWriterThread (OutputStream out, FileInputStream inputStream, String aspectName, int limit) {
			o = out;
		//	this.networkId = networkId;
			aspect = aspectName;
			this.limit = limit;
			in = inputStream;
		}
		
		@Override
		public void run() {

			try {
				ObjectMapper om = new ObjectMapper();
				Iterator<CxAspectElement<?>> it = om.readerFor(CxAspectElement.getCxClassFromAspectName(aspect)).readValues(in);
				
				try (CX2AspectWriter<CxAspectElement<?>> wtr = new CX2AspectWriter<>(o)) {
					for ( int i = 0 ; i < limit && it.hasNext() ; i++) {
						wtr.writeCXElement(it.next());
					}
				}
			} catch (IOException e) {
				accLogger.error("IOException in CX2AspectElementWriterThread: " + e.getMessage());
			} catch (Exception e1) {
				accLogger.error("Ndex exception: " + e1.getMessage());
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					accLogger.error("Failed to close outputstream in CX2ElementWriterWriterThread");
					e.printStackTrace();
				}
			} 
		}
		
	}


	private class CX2RandomEdgeWriterThread extends Thread {
		private OutputStream o;
	//	private String networkId;
		private FileInputStream in;
		private TreeSet<Long> positions;
		public CX2RandomEdgeWriterThread (OutputStream out, FileInputStream inputStream, TreeSet<Long> positions) {
			o = out;
		//	this.networkId = networkId;
			this.positions = positions;
			in = inputStream;
		}
		
		@Override
		public void run() {

			try {
				ObjectMapper om = new ObjectMapper();
				Iterator<CxEdge> it = om.readerFor(CxEdge.class).readValues(in);
				
				Long currentPosition = positions.pollFirst();
				long counter = 0;
				try (CX2AspectWriter<CxEdge> wtr = new CX2AspectWriter<>(o)) {
					while ( it.hasNext()) {
						CxEdge currentEdge = it.next();
						if ( currentPosition.longValue() == counter) {
							wtr.writeCXElement(currentEdge);
							if (positions.size() ==0)
								break;
							
							currentPosition = positions.pollFirst();
						}
						counter ++;						
					}
				}
			} catch (IOException e) {
				accLogger.error("IOException in CX2AspectElementWriterThread: " + e.getMessage());
			} catch (Exception e1) {
				accLogger.error("Ndex exception: " + e1.getMessage());
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					accLogger.error("Failed to close outputstream in CX2ElementWriterWriterThread");
					e.printStackTrace();
				}
			} 
		}
		
	}
	
	
	
	   @POST	   
	   @Path("")
	   @Produces("application/json")
	   @Consumes(MediaType.APPLICATION_JSON)
	   @Operation(summary = "Creates CX2 network",
			      description = "Submits request to create [CX2](https://cytoscape.org/cx/cx2/specification/cytoscape-exchange-format-specification-(version-2)/) Network on server.<br/> "
						  + "The [CX2](https://cytoscape.org/cx/cx2/specification/cytoscape-exchange-format-specification-(version-2)/) Network "
						  + "should be passed as body of this request in [CX2 JSON format](https://cytoscape.org/cx/cx2/specification/cytoscape-exchange-format-specification-(version-2)/)<br/><br/>"
						  + "**NOTE:** Network may not be accessible while server performs validation and indexing.<br/>To check status call *v3/networks/{networkid}/summary*",
				  requestBody = @RequestBody(description="CX2 Network in JSON format", required=true, 
						                     content=@Content(mediaType="application/json",
													          schema = @Schema(example="[{\"CXVersion\": \"2.0\", \"hasFragments\": false}, {\"metaData\": [{\"elementCount\": 1, \"name\": \"attributeDeclarations\"}, {\"elementCount\": 2, \"name\": \"nodes\"}, {\"elementCount\": 1, \"name\": \"edges\"}]}, {\"attributeDeclarations\": [{\"nodes\": {\"name\": {\"d\": \"string\"}, \"age\": {\"d\": \"integer\"}}, \"edges\": {\"weight\": {\"d\": \"double\"}}}]}, {\"nodes\": [{\"id\": 0, \"v\": {\"name\": \"node 1\", \"age\": 5}}, {\"id\": 1, \"v\": {\"name\": \"node 2\", \"age\": 10}}]}, {\"edges\": [{\"id\": 0, \"s\": 0, \"t\": 1, \"v\": {\"weight\": 0.3}}]}, {\"status\": [{\"error\": \"\", \"success\": true}]}]"))),
				  responses = {
					  @ApiResponse(responseCode = "201", description = "CX2 Network created successfully",
							       headers = @Header(name = "location", description = "URL containing resource generated by this request"),
							       content = @Content(mediaType = "application/json", 
										              schema = @Schema(implementation = NdexObjectUpdateStatus.class)))
				  })
	   public Response createNetworkJson( 
			   @Parameter(description="Can be set to **PUBLIC** (visible to all) or **PRIVATE** (visibile to only user and is default if not set)", example="PUBLIC") @QueryParam("visibility") String visibilityStr,
				@Parameter(description="Additional fields to index on network. **DO NOT USE, NOT IMPLEMENTED YET**") @QueryParam("indexedfields") String fieldListStr // comma separated list		
			   ) throws Exception
	   {
	   
		   VisibilityType visibility = null;
		   if ( visibilityStr !=null) {
			   visibility = VisibilityType.valueOf(visibilityStr);
		   }
		   
		   Set<String> extraIndexOnNodes = null;
		   if ( fieldListStr != null) {
			   extraIndexOnNodes = new HashSet<>(10);
			   for ( String f: fieldListStr.split("\\s*,\\s*") ) {
				   extraIndexOnNodes.add(f);
			   }
		   }
		   try (UserDAO dao = new UserDAO()) {
			   dao.checkDiskSpace(getLoggedInUserId());
		   }
		   
		   try (InputStream in = this.getInputStreamFromRequest()) {
			   UUID uuid = storeRawNetworkFromStream(in, CX2NetworkLoader.cx2NetworkFileName);
			   return processRawCX2Network(visibility, extraIndexOnNodes, uuid);

		   }		   

	   	}

		private Response processRawCX2Network(VisibilityType visibility, Set<String> extraIndexOnNodes, UUID uuid)
				throws SQLException, NdexException, IOException, ObjectNotFoundException, JsonProcessingException,
				URISyntaxException {
			String uuidStr = uuid.toString();
			   accLogger.info("[data]\t[uuid:" +uuidStr + "]" );
		   
			   String urlStr = Configuration.getInstance().getHostURI() +"/v3/networks/"+ uuidStr;
			   
			   String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/" + CX2NetworkLoader.cx2NetworkFileName;
			   long fileSize = new File(cxFileName).length();

			   // create entry in db. 
			   NdexObjectUpdateStatus status;
		       try (NetworkDAO dao = new NetworkDAO()) {
		    	   status = 
		    			   dao.CreateEmptyNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName(), fileSize,null, CX2NetworkLoader.cx2Format);
	       
				   dao.commit();
		       }
		       
		       NdexServerQueue.INSTANCE.addSystemTask(new CX2NetworkLoadingTask(uuid, false, visibility, extraIndexOnNodes));		   
			   URI l = new URI (urlStr);
			   ObjectMapper om = new ObjectMapper();

			   return Response.created(l).header("Access-Control-Expose-Headers", "Location")
					   .entity(om.writeValueAsString(status)).build();
		}
		
		
		@POST

		@Path("")
		@Produces("application/json")
		@Consumes("multipart/form-data")
		public Response createCX2Network(MultipartFormDataInput input, @QueryParam("visibility") String visibilityStr,
				@QueryParam("indexedfields") String fieldListStr // comma seperated list
		) throws Exception {

			VisibilityType visibility = null;
			if (visibilityStr != null) {
				visibility = VisibilityType.valueOf(visibilityStr);
			}

			Set<String> extraIndexOnNodes = null;
			if (fieldListStr != null) {
				extraIndexOnNodes = new HashSet<>(10);
				for (String f : fieldListStr.split("\\s*,\\s*")) {
					extraIndexOnNodes.add(f);
				}
			}
			try (UserDAO dao = new UserDAO()) {
				dao.checkDiskSpace(getLoggedInUserId());
			}

			UUID uuid = storeRawNetworkFromMultipart(input, CX2NetworkLoader.cx2NetworkFileName);
			return processRawCX2Network(visibility, extraIndexOnNodes, uuid);

		}
		
		
	    @PUT
	    @Path("/{networkid}")
		@Operation(summary = "Update a Network", description = "Update the specified network with new content, based on CX2 data. If the content type is multipart/form-data, the CX2 data is in the CXNetworkStream field of PUTed multipart/form-data.  If the content type is application/json, the CX data is in the payload. Errors if the CX data is not provided Errors if the UUID does not correspond to an existing network on the NDEx Server for which the authenticated user owns or for which they have WRITE permission.")
	    @Consumes(MediaType.APPLICATION_JSON)
	    @Produces("application/json")

	    public NdexObjectUpdateStatus updateNetworkJson(final @PathParam("networkid") String networkIdStr,
	    		 @QueryParam("visibility") String visibilityStr,
			 @QueryParam("extranodeindex") String fieldListStr // comma seperated list		
	    		) throws Exception 
	    {
	    	  VisibilityType visibility = null;
			   if ( visibilityStr !=null) {
				   visibility = VisibilityType.valueOf(visibilityStr);
			   }
			   
			   Set<String> extraIndexOnNodes = null;
			   if ( fieldListStr != null) {
				   extraIndexOnNodes = new HashSet<>(10);
				   for ( String f: fieldListStr.split("\\s*,\\s*") ) {
					   extraIndexOnNodes.add(f);
				   }
			   }
				    	
	        UUID networkId = UUID.fromString(networkIdStr);
	        
	        NdexObjectUpdateStatus s;
	        try ( NetworkDAO daoNew = lockNetworkForUpdate(networkId) ) {
				
				try (InputStream in = this.getInputStreamFromRequest()) {
						UUID tmpNetworkId = storeRawNetworkFromStream(in, CX2NetworkLoader.cx2NetworkFileName);

					s =	updateCx2NetworkFromSavedFile(networkId, daoNew, tmpNetworkId);
				
	           } catch (SQLException | NdexException | IOException e) {
	        	  // e.printStackTrace();
	        	   daoNew.rollback();
	        	   daoNew.unlockNetwork(networkId);  

	        	   throw e;
	           } 
				
				
	        }  
	    	      
		     NdexServerQueue.INSTANCE.addSystemTask(new CX2NetworkLoadingTask(networkId, true, visibility,extraIndexOnNodes));
		     return s; 
	    }
	    
	    
	    @PUT
	    @Path("/{networkid}")
	    @Consumes("multipart/form-data")
	    @Produces("application/json")

	    public NdexObjectUpdateStatus updateCX2Network(final @PathParam("networkid") String networkIdStr,
	    		 @QueryParam("visibility") String visibilityStr,
			 @QueryParam("extranodeindex") String fieldListStr, // comma seperated list		
	    		MultipartFormDataInput input) throws Exception 
	    {
	    	  VisibilityType visibility = null;
			   if ( visibilityStr !=null) {
				   visibility = VisibilityType.valueOf(visibilityStr);
			   }
			   
			   Set<String> extraIndexOnNodes = null;
			   if ( fieldListStr != null) {
				   extraIndexOnNodes = new HashSet<>(10);
				   for ( String f: fieldListStr.split("\\s*,\\s*") ) {
					   extraIndexOnNodes.add(f);
				   }
			   }
	    	
	        UUID networkId = UUID.fromString(networkIdStr);
	        
            NdexObjectUpdateStatus s;
            
	        try ( NetworkDAO daoNew =lockNetworkForUpdate(networkId) ) {
				try {			
					UUID tmpNetworkId = storeRawNetworkFromMultipart (input, CX2NetworkLoader.cx2NetworkFileName);

					s = updateCx2NetworkFromSavedFile( networkId, daoNew, tmpNetworkId);
				
				} catch (SQLException | NdexException | IOException e) {
	        	   e.printStackTrace();
	        	   daoNew.rollback();
	        	   daoNew.unlockNetwork(networkId);  
	        	   throw e;
	           } 
	        }	
	    	      
		    NdexServerQueue.INSTANCE.addSystemTask(new CX2NetworkLoadingTask(networkId, true, visibility,extraIndexOnNodes));
		    
		    return s;
	    }


		private static NdexObjectUpdateStatus updateCx2NetworkFromSavedFile(UUID networkId, NetworkDAO daoNew,
				UUID tmpNetworkId) throws SQLException, NdexException, IOException, JsonParseException,
				JsonMappingException, ObjectNotFoundException {
			String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId.toString()
					+ "/" + CX2NetworkLoader.cx2NetworkFileName;
			long fileSize = new File(cxFileName).length();

			NdexObjectUpdateStatus status = daoNew.clearNetworkSummary(networkId, fileSize);

			java.nio.file.Path src = Paths
					.get(Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId);
			java.nio.file.Path tgt = Paths
					.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId);
			FileUtils.deleteDirectory(
					new File(Configuration.getInstance().getNdexRoot() + "/data/" + networkId));
			Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

			daoNew.commit();
			return status;
		}

		
		@PermitAll
		@GET
		@Path("/{networkid}/export")
		@Operation(summary = "Export nodes or edges in TSV format", description = "Returns the nodes or edges of this network specified by networkid in TSV format. Content type of the response is text/tab-separated-values.")

		public Response exportTSVText( 
				@PathParam("networkid") final String networkId,
				@QueryParam("accesskey") String accessKey,
				@DefaultValue("node")  @QueryParam("type") String type,
				@DefaultValue("true")  @QueryParam("header") boolean includeHeader,
				@DefaultValue(",")  @QueryParam("listdelimiter") String listDelimiter,
				@DefaultValue("id")  @QueryParam("nodekey") String nodeKey,
				@QueryParam("nodeattributes") String nodeAttrStr,
				@QueryParam("edgeattributes") String edgeAttrStr,
				@DefaultValue("false")  @QueryParam("quotestringinlist") boolean quoteStringInList
				) throws NdexException, SQLException, IOException {
			
			if  (!type.equals("node") && !type.equals("edge") )
				throw new BadRequestException("Parameter \"type\" can only be 'node' or 'edge'.");	
			
			UUID networkUUID = UUID.fromString(networkId);
	    	
	    	try (NetworkDAO dao = new NetworkDAO()) {
	    		if ( !dao.isReadable(networkUUID, getLoggedInUserId()) && 
	    				! dao.accessKeyIsValid(networkUUID, accessKey)) {
	    			throw new UnauthorizedOperationException("User doesn't have access to this network.");
	    		}
	    		
	    		CxAttributeDeclaration attrDecls = Utilities.getAttributeDecls( networkUUID);
		 		String[] nodeAttrs = ( nodeAttrStr == null ) ? null : nodeAttrStr.split(",");
	    		String[] edgeAttrs = edgeAttrStr == null? null: edgeAttrStr.split(",");
	    		
	    		//Check if all attributes are valid, and check if listDelimiter contains '"'.
	    		if ( nodeAttrs !=null) {
	    			if ( attrDecls == null)
	    				throw new UnauthorizedOperationException("This network doesn't have node attributes");
	    			
	    			Map<String,DeclarationEntry> decls = attrDecls.getAttributesInAspect(CxNode.ASPECT_NAME);
	    			if ( decls == null)
	    				throw new UnauthorizedOperationException("This network doesn't have node attributes");
	    			for ( String nAttr : nodeAttrs) {
	    				if ( !decls.containsKey(nAttr))
	    					throw new UnauthorizedOperationException( CxNode.ASPECT_NAME + " aspect doesn't have attribute " + nAttr);
	    			}
	    		}
	    		if ( edgeAttrs !=null) {
	    			if ( attrDecls == null)
	    				throw new UnauthorizedOperationException("This network doesn't have edge attributes");
	    		
	    			Map<String,DeclarationEntry> decls = attrDecls.getAttributesInAspect(CxEdge.ASPECT_NAME);
	    			if ( decls == null)
	    				throw new UnauthorizedOperationException("This network doesn't have edge attributes");
	    			for ( String eAttr : edgeAttrs) {
	    				if ( !decls.containsKey(eAttr))
	    					throw new UnauthorizedOperationException( CxEdge.ASPECT_NAME + " aspect doesn't have attribute " + eAttr);
	    			}
	    		}
	    		
	    		if ( listDelimiter.indexOf('"') != -1)
	    			throw new UnauthorizedOperationException("listdelimiter parameter can't contain '\"'\".");
	    		
	    		if ( !nodeKey.equals("id")) {
	    			if ( attrDecls == null )
	    				throw new UnauthorizedOperationException("This network doesn't have node attributes");
	    			Map<String,DeclarationEntry> decls = attrDecls.getAttributesInAspect(CxNode.ASPECT_NAME);
	    			if ( decls == null)
	    				throw new UnauthorizedOperationException("This network doesn't have node attributes");
	    			if ( !decls.containsKey(nodeKey))
	    				throw new UnauthorizedOperationException( CxNode.ASPECT_NAME + " aspect doesn't have attribute " + nodeKey);
	    		}
	    		
				PipedInputStream in = new PipedInputStream();
				 
				PipedOutputStream out;
				
		 		try {
					out = new PipedOutputStream(in);
				} catch (IOException e) {
					in.close();
					throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
				}
		 		
		 		
	    		new TSVWriterThread(out, networkUUID, attrDecls, type, includeHeader, listDelimiter, nodeKey, 
	    				nodeAttrs, edgeAttrs, quoteStringInList).start();
	    		
	    		return Response.ok().type("text/tab-separated-values").entity(in).build();

	    	} 	
		}
		
		
		
		@PermitAll
		@GET
		@Path("/{networkid}/DOI")
		@Produces("text/plain")

		public String mintDOI( 
				@PathParam("networkid") final String networkId,
				@QueryParam("key") String key,
				@QueryParam("email") String submitter
				) throws Exception {

			String uuidFromKey = Security.decrypt(key,Configuration.getInstance().getSecretKeySpec());
			String submitterEmail = submitter;
			
			if( !networkId.equals(uuidFromKey)) 
				throw new BadRequestException("Invalid key in the URL.");
			
			UUID networkUUID = UUID.fromString(networkId);
			
			try (NetworkDAO dao = new NetworkDAO() ) {
				String currentDOI = dao.getNetworkDOI(networkUUID);
				if ( currentDOI ==null || !currentDOI.equals(NetworkDAO.PENDING)) {
					throw new ForbiddenOperationException("This operation only works when a DOI is pending. The current value of DOI is: " + currentDOI );
				}
				dao.setDOI(networkUUID, "CREATING");
				dao.commit();
				
				NetworkSummary s = dao.getNetworkSummaryById(networkUUID);
				
				String author = null;
				for (NdexPropertyValuePair p : s.getProperties() ) {
					if ( p.getPredicateString().equals("author"))
						author = p.getValue();
					
				}
				if ( author == null)  {
					dao.setDOI(networkUUID, NetworkDAO.PENDING);
					dao.commit();
					throw new NdexException("Property author is missing in the network.");
				}
				
				String url = Configuration.getInstance().getHostURI() + "/viewer/networks/"+ networkId;
				
				if ( dao.getNetworkVisibility(networkUUID) == VisibilityType.PRIVATE) {
					url += "?accesskey=" + dao.getNetworkAccessKey(networkUUID);
				}

				String id;
				try {
					id = EZIDClient.createDOI(
							url ,
							author, s.getName(),
							Configuration.getInstance().getDOIPrefix(),
							Configuration.getInstance().getDOIUser(),
							Configuration.getInstance().getDOIPswd());
				} catch (Exception e) {
					dao.setDOI(networkUUID, NetworkDAO.PENDING);
					dao.commit();
					e.printStackTrace();
					throw new NdexException("Failed to create DOI in EZID site. Cause: " + e.getMessage());
					
				}
				
				dao.setDOI(networkUUID, id);
				dao.commit();
				
				//Send confirmation to submitter and admin
				
				//Reading in the email template
				String emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");
				String adminEmailAddress = Configuration.getInstance().getAdminUserEmail();

				String messageBody = "Dear NDEx user " + s.getOwner() + ",<p>"
						+ "Your DOI request for the network<br>"
						+ s.getName() + "(" + networkId + ")<br>"
						+ "has been processed.<p>"
						+ "You digital Object Identifier (DOI) is:<br>"
						+ id + "<p>"
						+ "Your identifier's URL form is:<br>"
						+ "https://doi.org/" + id + "<p>"
						+ "Please be advised that it can take several hours before your new DOI becomes resolvable.";
						
				
		        String htmlEmail = emailTemplate.replaceFirst("%%____%%", 
		        		Matcher.quoteReplacement(messageBody)) ;

		        AmazonSESMailSender.getInstance().sendEmail(submitterEmail, 
		        		  htmlEmail, "A DOI has been created for your NDEx Network", "html",adminEmailAddress);

				
				return "DOI " + id +" has been created on this network. Confirmation emails have been sent."; 
			}
			
		}
		
		@PermitAll
		@GET
		@Path("/{networkid}/summary")
		@Operation(summary = "Get a Network Summary", description = "Retrieves a NetworkSummary JSON object based on the network specified by networkId. A NetworkSummary object is a subset of a network object. It is used to convey basic information about a network in this API. NOTE: If value of ‘completed’ is False this result may not contain all attributes below (name, description, version might be missing, nodeCount and edgeCount will be zero, properties will be empty, etc…)")
		@Produces("application/json")
		
		public NetworkSummaryV3 getNetworkSummaryV3(
				@PathParam("networkid") final String networkIdStr ,
				@QueryParam("accesskey") String accessKey ,
				@DefaultValue("FULL") @QueryParam("format") String format
				/*@Context org.jboss.resteasy.spi.HttpResponse response*/ )

				throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
			
			try {
			NetworkSummaryFormat fmt = NetworkSummaryFormat.valueOf(format.toUpperCase());
					
			try (NetworkDAO dao = new NetworkDAO())  {
				UUID userId = getLoggedInUserId();
				UUID networkId = UUID.fromString(networkIdStr);
				if ( dao.isReadable(networkId, userId) || dao.accessKeyIsValid(networkId, accessKey)) {
					NetworkSummaryV3 summary = dao.getNetworkMetadataById(networkId,fmt);
					return summary;	
				}
					
				throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
			}  	
			} catch(IllegalArgumentException e) {
				throw new BadRequestException("Format " + format + " is unsupported. Error message: " + e.getMessage());
			}
		} 
		
	
}
