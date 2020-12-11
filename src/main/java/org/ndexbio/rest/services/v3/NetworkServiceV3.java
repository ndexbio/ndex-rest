package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.io.FileUtils;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.CxAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.io.CX2AspectWriter;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    				if ( getGoogleAuthenticator() == null)
    					throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
    				userId = getGoogleAuthenticator().getUserUUIDByIdToken(id_token);
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
	@Path("/{networkid}/edges") 
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
					
				} catch (FileNotFoundException e) {
						throw new ObjectNotFoundException("Aspect CxEdge is not found in this network: " + e.getMessage());
				}
	    	}	
		}
	}

	
	@PermitAll
	@GET
	@Path("/{networkid}/{aspectname}")
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
					throw new ObjectNotFoundException("Aspect "+ aspectName + " not found in this network: " + e.getMessage());
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
	
	
/*	
	   @POST	   
	   @Path("")
	   @Produces("text/plain")
	   @Consumes(MediaType.APPLICATION_JSON)
	   public Response createNetworkJson( 
			   @QueryParam("visibility") String visibilityStr,
				@QueryParam("indexedfields") String fieldListStr // comma seperated list		
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
		   
			   String urlStr = Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ uuidStr;
			   
			   String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/" + CX2NetworkLoader.cx2NetworkFileName;
			   long fileSize = new File(cxFileName).length();

			   // create entry in db. 
		       try (NetworkDAO dao = new NetworkDAO()) {
		    	  // NetworkSummary summary = 
		    			   dao.CreateEmptyNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName(), fileSize,null, CX2NetworkLoader.cx2Format);
	       
					dao.commit();
		       }
		       
		       NdexServerQueue.INSTANCE.addSystemTask(new CX2NetworkLoadingTask(uuid, false, visibility, extraIndexOnNodes));		   
			   URI l = new URI (urlStr);

			   return Response.created(l).build();
		}
		
		
		@POST

		@Path("")
		@Produces("text/plain")
		@Consumes("multipart/form-data")
		public Response createCXNetwork(MultipartFormDataInput input, @QueryParam("visibility") String visibilityStr,
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
	    @Consumes(MediaType.APPLICATION_JSON)
	    @Produces("application/json")

	    public void updateNetworkJson(final @PathParam("networkid") String networkIdStr,
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

	        try ( NetworkDAO daoNew = lockNetworkForUpdate(networkId) ) {
				
				try (InputStream in = this.getInputStreamFromRequest()) {
						UUID tmpNetworkId = storeRawNetworkFromStream(in, CX2NetworkLoader.cx2NetworkFileName);

						updateCx2NetworkFromSavedFile(networkId, daoNew, tmpNetworkId);
				
	           } catch (SQLException | NdexException | IOException e) {
	        	  // e.printStackTrace();
	        	   daoNew.rollback();
	        	   daoNew.unlockNetwork(networkId);  

	        	   throw e;
	           } 
				
				
	        }  
	    	      
		     NdexServerQueue.INSTANCE.addSystemTask(new CX2NetworkLoadingTask(networkId, true, visibility,extraIndexOnNodes));
		    // return networkIdStr; 
	    }
	    
	    
	    @PUT
	    @Path("/{networkid}")
	    @Consumes("multipart/form-data")
	    @Produces("application/json")

	    public void updateCXNetwork(final @PathParam("networkid") String networkIdStr,
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

	        try ( NetworkDAO daoNew =lockNetworkForUpdate(networkId) ) {
				try {			
					UUID tmpNetworkId = storeRawNetworkFromMultipart (input, CX2NetworkLoader.cx2NetworkFileName);

					updateCx2NetworkFromSavedFile( networkId, daoNew, tmpNetworkId);
				
				} catch (SQLException | NdexException | IOException e) {
	        	   e.printStackTrace();
	        	   daoNew.rollback();
	        	   daoNew.unlockNetwork(networkId);  
	        	   throw e;
	           } 
	        }	
	    	      
		    NdexServerQueue.INSTANCE.addSystemTask(new CX2NetworkLoadingTask(networkId, true, visibility,extraIndexOnNodes));
	    }
*/

		private static void updateCx2NetworkFromSavedFile(UUID networkId, NetworkDAO daoNew,
				UUID tmpNetworkId) throws SQLException, NdexException, IOException, JsonParseException,
				JsonMappingException, ObjectNotFoundException {
			String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId.toString()
					+ "/" + CX2NetworkLoader.cx2NetworkFileName;
			long fileSize = new File(cxFileName).length();

			daoNew.clearNetworkSummary(networkId, fileSize);

			java.nio.file.Path src = Paths
					.get(Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId);
			java.nio.file.Path tgt = Paths
					.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId);
			FileUtils.deleteDirectory(
					new File(Configuration.getInstance().getNdexRoot() + "/data/" + networkId));
			Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

			daoNew.commit();
		}

}
