package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.task.CX2NetworkLoadingTask;
import org.ndexbio.task.NdexServerQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

@Path("/v3/network")
public class NetworkServiceV3Temp  extends NdexService {
	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	static private final String readOnlyParameter = "readOnly";

	public NetworkServiceV3Temp(@Context HttpServletRequest httpRequest
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
  
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/net2.cx";

    	try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
		//	setZipFlag();
			ResponseBuilder r = Response.ok();
			if (isDownload) {
				if (title == null || title.length() < 1) {
					title = networkId;
				}
				title.replace('"', '_');
				r.header("Content-Disposition", "attachment; filename=\"" + title + ".cx\"");
				r.header("Access-Control-Expose-Headers", "Content-Disposition");
			}
			return 	r.type(isDownload ? MediaType.APPLICATION_OCTET_STREAM_TYPE : MediaType.APPLICATION_JSON_TYPE)
					.entity(in).build();
		} catch (IOException e) {
			throw new NdexException ("Ndex server IO error: " + e.getMessage());
		}
		
	}  
	
	
	
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
		    			   dao.CreateEmptyNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName(), fileSize,null);
	       
					dao.commit();
		       }
		       
		       NdexServerQueue.INSTANCE.addSystemTask(new CX2NetworkLoadingTask(uuid, false, visibility, extraIndexOnNodes));		   
			   URI l = new URI (urlStr);

			   return Response.created(l).entity(l).build();
		}

}
