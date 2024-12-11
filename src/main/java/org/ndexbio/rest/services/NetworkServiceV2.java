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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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
import org.apache.solr.client.solrj.SolrServerException;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.cx.CX2NetworkFileGenerator;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.persistence.CXNetworkAspectsUpdater;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.AttributeDeclaredAspect;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.converter.AspectAttributeStat;
import org.ndexbio.cx2.io.CX2AspectWriter;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.core.CXAspectWriter;
import org.ndexbio.cxio.core.OpaqueAspectIterator;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.cxio.util.JsonWriter;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.InvalidNetworkException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.NetworkConcurrentModificationException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.task.CXNetworkLoadingTask;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskDeleteNetwork;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;

@Path("/v2/network")
public class NetworkServiceV2 extends NdexService {
	
//	static Logger logger = LoggerFactory.getLogger(NetworkService.class);
	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	
	static private final String readOnlyParameter = "readOnly";
	
	private static final String cx1NetworkFileName = "network.cx";

	public NetworkServiceV2(@Context HttpServletRequest httpRequest
		//	@Context org.jboss.resteasy.spi.HttpResponse response
			) {
		super(httpRequest);
	}



    /**************************************************************************
    * Returns network provenance.
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonParseException
     * @throws NdexException
     * @throws SQLException 
    *
    **************************************************************************/
	@PermitAll
	@GET
	@Path("/{networkid}/provenance")
	@Produces("application/json")

	public ProvenanceEntity getProvenance(
			@PathParam("networkid") final String networkIdStr,
			@QueryParam("accesskey") String accessKey)

			throws IllegalArgumentException, JsonParseException, JsonMappingException, IOException, NdexException, SQLException {
		
		
		UUID networkId = UUID.fromString(networkIdStr);
		
		try (NetworkDAO daoNew = new NetworkDAO()) {
			if ( !daoNew.isReadable(networkId, getLoggedInUserId()) && (!daoNew.accessKeyIsValid(networkId, accessKey)))
					throw new UnauthorizedOperationException("Network " + networkId + " is not readable to this user.");

			return daoNew.getProvenance(networkId);

		} 
	}

    /**************************************************************************
    * Updates network provenance.
     * @throws Exception
    *
    **************************************************************************/
    @PUT
	@Path("/{networkid}/provenance")
	@Produces("application/json")
  public void setProvenance(@PathParam("networkid")final String networkIdStr, final ProvenanceEntity provenance)
    		throws Exception {
    
		User user = getLoggedInUser();

		setProvenance_aux(networkIdStr, provenance, user);
    }


    @Deprecated
	protected static void setProvenance_aux(final String networkIdStr, final ProvenanceEntity provenance, User user)
			throws Exception {
		try (NetworkDAO daoNew = new NetworkDAO()){
			
			UUID networkId = UUID.fromString(networkIdStr);

			if ( !daoNew.isWriteable(networkId, user.getExternalId())) {

		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			}
	
			if(daoNew.isReadOnly(networkId)) {
				daoNew.close();
				throw new NdexException ("Can't update readonly network.");
			} 


			if (!daoNew.networkIsValid(networkId))
				throw new InvalidNetworkException();
				
	/*		if ( daoNew.networkIsLocked(networkId,6)) {
				daoNew.close();
				throw new NetworkConcurrentModificationException ();
			}
			
			daoNew.lockNetwork(networkId); */
			daoNew.setProvenance(networkId, provenance);
			daoNew.commit();
			
			//Recreate the CX file 					
		//	NetworkSummary fullSummary = daoNew.getNetworkSummaryById(networkId);
		//	MetaDataCollection metadata = daoNew.getMetaDataCollection(networkId);
	//		CXNetworkFileGenerator g = new CXNetworkFileGenerator(networkId, daoNew, new Provenance(provenance));
	//		g.reCreateCXFile();
	//		daoNew.unlockNetwork(networkId);
			
			
			return ; // provenance; //  daoNew.getProvenance(networkUUID);
		} catch (Exception e) {
			//if (null != daoNew) daoNew.rollback();
			throw e;
		} 
	}



    /**************************************************************************
    * Sets network properties.
     * @throws Exception
    *
    **************************************************************************/
    @PUT
	@Path("/{networkid}/properties")
	@Produces("application/json")
    
    public int setNetworkProperties(
    		@PathParam("networkid")final String networkId,
    		final List<NdexPropertyValuePair> properties)
    		throws Exception {

		User user = getLoggedInUser();
		UUID networkUUID = UUID.fromString(networkId);

		if ( properties.stream().anyMatch(x -> { String n = x.getPredicateString(); 
		                                    return n.equals(NdexClasses.Network_P_name) || n.equals(NdexClasses.Network_P_desc)
		                                    		|| n.equals(NdexClasses.Network_P_version);}) ) {
			throw new BadRequestException (
					"Property list can't contain 'name', 'description' or 'version'. You need to use the "
					+ "update network profile function to modify these 3 properties.");
		}
		
		
		
		try (NetworkDAO daoNew = new NetworkDAO()) {
			
	  	    if(daoNew.isReadOnly(networkUUID)) {
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !daoNew.isWriteable(networkUUID, user.getExternalId())) {
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if ( daoNew.networkIsLocked(networkUUID,10)) {
				throw new NetworkConcurrentModificationException ();
			} 
			
			if ( !daoNew.networkIsValid(networkUUID))
				throw new InvalidNetworkException();

			daoNew.lockNetwork(networkUUID);
			
			int i = 0;
			try {
				i = daoNew.setNetworkProperties(networkUUID, properties);

				// recreate files and update db
				updateNetworkAttributesAspect(daoNew, networkUUID);

			} finally {
				daoNew.unlockNetwork(networkUUID);				
			}
			
			NetworkIndexLevel idxLvl = daoNew.getIndexLevel(networkUUID);
			if ( idxLvl != NetworkIndexLevel.NONE) {
				daoNew.setFlag(networkUUID, "iscomplete",false);
				daoNew.commit();
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,SolrIndexScope.global,false,null,idxLvl,false));
			} else {
				daoNew.setFlag(networkUUID, "iscomplete", true);
				daoNew.commit();
			}
			
			return i;
		} catch (Exception e) {
			//logger.severe("Error occurred when update network properties: " + e.getMessage());
			//e.printStackTrace();
			//if (null != daoNew) daoNew.rollback();
			logger.error("Updating properties of network {}. Exception caught:]{}", networkId, e);
			
			throw new NdexException(e.getMessage(), e);
		} 
    }
  
	/*
	 *
	 * Operations returning Networks 
	 * 
	 */

	@PermitAll
	@GET
	@Path("/{networkid}/summary")
	@Produces("application/json")
	
	public NetworkSummary getNetworkSummary(
			@PathParam("networkid") final String networkIdStr ,
			@QueryParam("accesskey") String accessKey /*,
			@Context org.jboss.resteasy.spi.HttpResponse response*/)

			throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
		
		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			UUID networkId = UUID.fromString(networkIdStr);
			if ( dao.isReadable(networkId, userId) || dao.accessKeyIsValid(networkId, accessKey)) {
				NetworkSummary summary = dao.getNetworkSummaryById(networkId);

				return summary;
			}
				
			throw new UnauthorizedOperationException ("Unauthorized access to network " + networkId);
		}  
			
			
	}


	@PermitAll
	@GET
	@Path("/{networkid}/aspect")
	
	public Response getNetworkCXMetadataCollection(	@PathParam("networkid") final String networkId,
			@QueryParam("accesskey") String accessKey)
			throws Exception {

    	UUID networkUUID = UUID.fromString(networkId);

		try (NetworkDAO dao = new NetworkDAO() ) {
			if ( dao.isReadable(networkUUID, getLoggedInUserId()) || dao.accessKeyIsValid(networkUUID, accessKey)) {
				MetaDataCollection mdc = dao.getMetaDataCollection(networkUUID);
		    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
				JsonWriter wtr = JsonWriter.createInstance(baos,true);
				mdc.toJson(wtr);
				String s = baos.toString();//"java.nio.charset.StandardCharsets.UTF_8");
				return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(s).build();
//		    	return mdc;
			}
			throw new UnauthorizedOperationException("User doesn't have access to this network.");
		}
	}  
	
	@PermitAll
	@GET
	@Path("/{networkid}/aspect/{aspectname}/metadata")
	@Produces("application/json")
	
	public MetaDataElement getNetworkCXMetadata(	
			@PathParam("networkid") final String networkId,
			@PathParam("aspectname") final String aspectName
			)
			throws Exception {

    	UUID networkUUID = UUID.fromString(networkId);

		try (NetworkDAO dao = new NetworkDAO() ) {
			if ( dao.isReadable(networkUUID, getLoggedInUserId())) {
				MetaDataCollection mdc = dao.getMetaDataCollection(networkUUID);
		    	return mdc.getMetaDataElement(aspectName);
			}
			throw new UnauthorizedOperationException("User doesn't have access to this network.");
		}
	}  
	
	
	@PermitAll
	@GET
	@Path("/{networkid}/aspect/{aspectname}")
	public Response getAspectElements(	@PathParam("networkid") final String networkId,
			@PathParam("aspectname") final String aspectName,
			@DefaultValue("-1") @QueryParam("size") int limit) throws SQLException, NdexException
		 {

    	UUID networkUUID = UUID.fromString(networkId);
    	
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( !dao.isReadable(networkUUID, getLoggedInUserId())) {
    			throw new UnauthorizedOperationException("User doesn't have access to this network.");
    		}
    		
    		File cx1AspectDir = new File (Configuration.getInstance().getNdexRoot() + "/data/" + networkId 
    				+ "/" + CXNetworkLoader.CX1AspectDir);
    		boolean hasCX1AspDir = cx1AspectDir.exists();
    		
			FileInputStream in = null;
			try {
				if ( hasCX1AspDir) {
				   in = new FileInputStream(cx1AspectDir + "/" + aspectName);
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
						
					new CXAspectElementsWriterThread(out,in, /*aspectName,*/ limit).start();
					return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(pin).build();
				} 
				
			} catch (FileNotFoundException e) {
					throw new ObjectNotFoundException("Aspect "+ aspectName + " not found in this network: " + e.getMessage());
			}
	    	
			
			//get aspect from cx2 aspects
			PipedInputStream pin = new PipedInputStream();
			PipedOutputStream out;
				
			try {
					out = new PipedOutputStream(pin);
			} catch (IOException e) {
				try {
					pin.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				throw new NdexException("IOExcetion when creating the piped output stream: "+ e.getMessage());
			}
							
			new CXAspectElementWriter2Thread(out,networkId, aspectName, limit, accLogger).start();
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(pin).build();
		
		
    	}

	}  
	
	
	@PermitAll
	@GET
	@Path("/{networkid}")

	public Response getCompleteNetworkAsCX(	@PathParam("networkid") final String networkId,
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
    				if ( getOAuthAuthenticator() == null)
    					throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
    				userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
    			}
    		}
    		if ( ! dao.isReadable(networkUUID, userId) && (!dao.accessKeyIsValid(networkUUID, accessKey))) 
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");
    		
    		title = dao.getNetworkName(networkUUID);
    	}
  
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + cx1NetworkFileName;

    	try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
		//	setZipFlag();
//			logger.info("[end: Return network {}]", networkId);
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
	//		logger.error("[end: Ndex server can't find file: {}]", e.getMessage());
			throw new NdexException ("Ndex server can't find file: " + e.getMessage());
		}
		
	}  


	
	@PermitAll
	@GET
	@Path("/{networkid}/sample")

	public Response getSampleNetworkAsCX(	@PathParam("networkid") final String networkIdStr ,
			@QueryParam("accesskey") String accessKey)
			throws IllegalArgumentException, NdexException, SQLException {
  	
    	UUID networkId = UUID.fromString(networkIdStr);
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( ! dao.isReadable(networkId, getLoggedInUserId()) && (!dao.accessKeyIsValid(networkId, accessKey)))
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");

    	}
    	
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/sample.cx";
		
		try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
			//	setZipFlag();
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
		} catch ( FileNotFoundException e) {
				throw new ObjectNotFoundException("Sample network of " + networkId + " not found. Error: " + e.getMessage());
		}  
		
	}  
	
	@GET
	@Path("/{networkid}/accesskey")
	@Produces("application/json")
	public Map<String,String> getNetworkAccessKey(@PathParam("networkid") final String networkIdStr)
			throws IllegalArgumentException, NdexException, SQLException {
  	
		UUID networkId = UUID.fromString(networkIdStr);
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( ! dao.isAdmin(networkId, getLoggedInUserId()))
                throw new UnauthorizedOperationException("User is not admin of this network.");

    		String key = dao.getNetworkAccessKey(networkId);
    		if (key == null || key.length()==0)
    			return null;
    		Map<String,String> result = new HashMap<>(1);
    		result.put("accessKey", key);
    		return result;
    	}
	}  
		
	@PUT
	@Path("/{networkid}/accesskey")
	@Produces("application/json")
	public Map<String,String> disableEnableNetworkAccessKey(@PathParam("networkid") final String networkIdStr,
			@QueryParam("action") String action)
			throws IllegalArgumentException, NdexException, SQLException {
  	
		UUID networkId = UUID.fromString(networkIdStr);
		if ( ! action.equalsIgnoreCase("disable") && ! action.equalsIgnoreCase("enable"))
			throw new NdexException("Value of 'action' paramter can only be 'disable' or 'enable'");
		
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( ! dao.isAdmin(networkId, getLoggedInUserId()))
                throw new UnauthorizedOperationException("User is not admin of this network.");

    		String key = null;
    		if ( action.equalsIgnoreCase("disable"))
    			dao.disableNetworkAccessKey(networkId);
    		else 
    			key = dao.enableNetworkAccessKey(networkId);
    		dao.commit();
    		
     		if (key == null || key.length()==0)
    			return null;
    		Map<String,String> result = new HashMap<>(1);
    		result.put("accessKey", key);
    		return result;
    	}
	}  
	
	
	@PUT
	@Path("/{networkid}/sample")
	
	public void setSampleNetwork(	@PathParam("networkid") final String networkId,
			String CXString)
			throws IllegalArgumentException, NdexException, SQLException, InterruptedException {
  	
		UUID networkUUID = UUID.fromString(networkId);
		try (NetworkDAO dao = new NetworkDAO()) {
			if (!dao.isAdmin(networkUUID, getLoggedInUserId()))
				throw new UnauthorizedOperationException("User is not admin of this network.");

			if (dao.networkIsLocked(networkUUID, 10))
				throw new NetworkConcurrentModificationException();

			if (!dao.networkIsValid(networkUUID))
				throw new InvalidNetworkException();

			String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/sample.cx";

			try (FileWriter w = new FileWriter(cxFilePath)) {
				w.write(CXString);
				dao.setFlag(networkUUID, "has_sample", true);
				dao.commit();
			} catch (IOException e) {
				throw new NdexException("Failed to write sample network of " + networkId + ": " + e.getMessage(), e);
			}
		}
	}  
	
	

	private class CXAspectElementsWriterThread extends Thread {
		private OutputStream o;
	//	private String networkId;
		private FileInputStream in;
	//	private String aspect;
		private int limit;
		public CXAspectElementsWriterThread (OutputStream out, FileInputStream inputStream, /*String aspectName,*/ int limit) {
			o = out;
		//	this.networkId = networkId;
		//	aspect = aspectName;
			this.limit = limit;
			in = inputStream;
		}
		
		@Override
		public void run() {

			try {
				OpaqueAspectIterator asi = new OpaqueAspectIterator(in);
				try (CXAspectWriter wtr = new CXAspectWriter (o)) {
					for ( int i = 0 ; i < limit && asi.hasNext() ; i++) {
						wtr.writeCXElement(asi.next());
					}
				}
			} catch (IOException e) {
					logger.error("IOException in CXAspectElementWriterThread: " + e.getMessage());
			} catch (Exception e1) {
				logger.error("Ndex exception: " + e1.getMessage());
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					logger.error("Failed to close outputstream in CXElementWriterWriterThread");
					e.printStackTrace();
				}
			} 
		}
		
	}



	/**************************************************************************
	 * Retrieves array of user membership objects
	 *
	 * @param networkId
	 *            The network ID.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The group doesn't exist.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @throws SQLException 
	 **************************************************************************/

	@GET
	@Path("/{networkid}/permission")
	@Produces("application/json")

	public Map<String, String> getNetworkUserMemberships(
			@PathParam("networkid") final String networkId,
		    @QueryParam("type") String sourceType,
			@QueryParam("permission") final String permissions ,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize) throws NdexException, SQLException {
		
		Permissions permission = null;
		if ( permissions != null ){
			permission = Permissions.valueOf(permissions.toUpperCase());
		} 
		
		UUID networkUUID = UUID.fromString(networkId);
		
		
		boolean returnUsers = true;
		if ( sourceType != null ) {
			if ( sourceType.toLowerCase().equals("group")) 
				returnUsers = false;
			else if ( !sourceType.toLowerCase().equals("user"))
				throw new NdexException("Invalid parameter 'type' " + sourceType + " received, it can only be 'user' or 'group'.");
		} else 
			throw new NdexException("Parameter 'type' is required in this function.");
		
		try (NetworkDAO networkDao = new NetworkDAO()) {
			if ( !networkDao.isAdmin(networkUUID, getLoggedInUserId()) ) 
				throw new UnauthorizedOperationException("Authenticate user is not the admin of this network.");
			
			Map<String,String> result = returnUsers?
					networkDao.getNetworkUserPermissions(networkUUID, permission, skipBlocks, blockSize):
					networkDao.getNetworkGroupPermissions(networkUUID,permission,skipBlocks,blockSize);
					
			return result;
		} 
	}

	
	@DELETE
	@Path("/{networkid}/permission")
	@Produces("application/json")

	public int deleteNetworkPermission(
			@PathParam("networkid") final String networkIdStr,
			@QueryParam("userid") String userIdStr,
			@QueryParam("groupid") String groupIdStr		
			)
			throws IllegalArgumentException, NdexException, IOException, SQLException {
				
		UUID networkId = UUID.fromString(networkIdStr);

		UUID userId = null;
		if ( userIdStr != null)
			userId = UUID.fromString(userIdStr);
		UUID groupId = null;
		if ( groupIdStr != null)
			groupId = UUID.fromString(groupIdStr);
		
		if ( userId == null && groupId == null)
			throw new NdexException ("Either userid or groupid parameter need to be set for this function.");
		if ( userId !=null && groupId != null)
			throw new NdexException ("userid and gorupid can't both be set for this function.");
		
		
		try (NetworkDAO networkDao = new NetworkDAO()){  
		//	User user = getLoggedInUser();
		//	networkDao.checkPermissionOperationCondition(networkId, user.getExternalId());
			
			if (!networkDao.isAdmin(networkId,getLoggedInUserId())) {
				if ( userId != null && !userId.equals(getLoggedInUserId())) {
					throw new UnauthorizedOperationException("Unable to delete network permisison: user need to be admin of this network or grantee of this permission.");
				}
				if ( groupId!=null ) {	
					throw new UnauthorizedOperationException("Unable to delete network permission: user is not an admin of this network.");
				}
			}

			if( !networkDao.networkIsValid(networkId))
				throw new InvalidNetworkException();
		
			int count;
			if ( userId !=null)
				count = networkDao.revokeUserPrivilege(networkId, userId);
			else 
				count = networkDao.revokeGroupPrivilege(networkId, groupId);

			// update the solr Index
			NetworkIndexLevel lvl = networkDao.getIndexLevel(networkId);
			if ( lvl != NetworkIndexLevel.NONE) {
				networkDao.setFlag(networkId, "iscomplete", false); 
				networkDao.commit();
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,null,lvl, false));
			}
            return count;
		} 
	}
	
	/*
	 *
	 * Operations on Network permissions
	 * 
	 */


	@PUT
	@Path("/{networkid}/permission")
	@Produces("application/json")
	public int updateNetworkPermission(
			@PathParam("networkid") final String networkIdStr,
			@QueryParam("userid") String userIdStr,
			@QueryParam("groupid") String groupIdStr,			
			@QueryParam("permission") final String permissions 
			)
			throws IllegalArgumentException, NdexException, IOException, SQLException {

		logger.info("[start: Updating membership for network {}]", networkIdStr);
		UUID networkId = UUID.fromString(networkIdStr);
		
		UUID userId = null;
		if ( userIdStr != null)
			userId = UUID.fromString(userIdStr);
		UUID groupId = null;
		if ( groupIdStr != null)
			groupId = UUID.fromString(groupIdStr);
		
		if ( userId == null && groupId == null)
			throw new NdexException ("Either userid or groupid parameter need to be set for this function.");
		if ( userId !=null && groupId != null)
			throw new NdexException ("userid and gorupid can't both be set for this function.");
		
		if ( permissions == null)
			throw new NdexException ("permission parameter is required in this function.");
		Permissions p = Permissions.valueOf(permissions.toUpperCase());
		
		try (NetworkDAO networkDao = new NetworkDAO()){
			
			User user = getLoggedInUser();
			
			if (!networkDao.isAdmin(networkId,user.getExternalId())) {
				throw new UnauthorizedOperationException("Unable to update network permission: user is not an admin of this network.");
			}
			
			if ( !networkDao.networkIsValid(networkId))
    			throw new InvalidNetworkException();
			
			
			int count;
			if ( userId!=null)  {
				count = networkDao.grantPrivilegeToUser(networkId, userId, p);
			} else 
				count = networkDao.grantPrivilegeToGroup(networkId, groupId, p);
			//networkDao.commit();
			
			// update the solr Index
			NetworkIndexLevel lvl = networkDao.getIndexLevel(networkId);
			if ( lvl != NetworkIndexLevel.NONE) {
				networkDao.setFlag(networkId, "iscomplete", false);
				networkDao.commit();
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,null,lvl, false));
			}
			
			logger.info("[end: Updated permission for network {}]", networkId);
	        return count;
		} 
	}	

	
	@PUT
	@Path("/{networkid}/reference")
	@Produces("application/json")
	public void updateReferenceOnPreCertifiedNetwork(@PathParam("networkid") final String networkId,
			Map<String,String> reference) throws SQLException, NdexException, SolrServerException, IOException {

		UUID networkUUID = UUID.fromString(networkId);
		UUID userId = getLoggedInUser().getExternalId();
		
		if ( reference.get("reference") == null) {
			throw new BadRequestException("Field reference is missing in the object.");
		}

		try (NetworkDAO networkDao = new NetworkDAO()){
			if(networkDao.isAdmin(networkUUID, userId) ) {

				if ( networkDao.hasDOI(networkUUID) && (!networkDao.isCertified(networkUUID)) ) {
					List<NdexPropertyValuePair> props = networkDao.getNetworkSummaryById(networkUUID).getProperties();
					boolean updated= false;
					for ( NdexPropertyValuePair p : props ) {
						if ( p.getPredicateString().equals("reference")) {
							p.setValue(reference.get("reference"));
							updated=true;
							break;
						}
					}
					if ( !updated) {
						props.add(new NdexPropertyValuePair("reference", reference.get("reference")));
					}
					
					networkDao.lockNetwork(networkUUID);

					networkDao.updateNetworkProperties(networkUUID,props);
					
					// recreate files and update db
					updateNetworkAttributesAspect(networkDao, networkUUID);

					networkDao.unlockNetwork(networkUUID);
					
					networkDao.updateNetworkVisibility(networkUUID, VisibilityType.PUBLIC, true);
					//networkDao.setFlag(networkUUID, "solr_indexed", true);
					networkDao.setIndexLevel(networkUUID,  NetworkIndexLevel.ALL);
					networkDao.setFlag(networkUUID, "certified", true);
					
					networkDao.setFlag(networkUUID, "iscomplete", false);
					networkDao.commit();
					NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,SolrIndexScope.global,false,null,NetworkIndexLevel.ALL, false));
					
				} else {
					if ( networkDao.isCertified(networkUUID))
						throw new ForbiddenOperationException("This network has already been certified, updating reference is not allowed.");
					
					throw new ForbiddenOperationException("This network doesn't have a DOI or a pending DOI request.");
				}
			}
		
		}	
		
	}
	
	@PUT
	@Path("/{networkid}/profile")
	@Produces("application/json")

	public void updateNetworkProfile(
			@PathParam("networkid") final String networkId,
			final NetworkSummary partialSummary
			)
            throws  NdexException, SQLException , IOException, IllegalArgumentException 
    {
		
		try (NetworkDAO networkDao = new NetworkDAO()){

			User user = getLoggedInUser();
			UUID networkUUID = UUID.fromString(networkId);
	
	  	    if(networkDao.isReadOnly(networkUUID)) {
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !networkDao.isWriteable(networkUUID, user.getExternalId())) {
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			Map<String,String> newValues = new HashMap<> ();
//	        List<SimplePropertyValuePair> entityProperties = new ArrayList<>();

			if ( partialSummary.getName() != null) {
				newValues.put(NdexClasses.Network_P_name, partialSummary.getName());
//			    entityProperties.add( new SimplePropertyValuePair("dc:title", partialSummary.getName()) );
			}
					
			if ( partialSummary.getDescription() != null) {
					newValues.put( NdexClasses.Network_P_desc, partialSummary.getDescription());
//		            entityProperties.add( new SimplePropertyValuePair("description", partialSummary.getDescription()) );
			}
				
			if ( partialSummary.getVersion()!=null ) {
					newValues.put( NdexClasses.Network_P_version, partialSummary.getVersion());
//		            entityProperties.add( new SimplePropertyValuePair("version", partialSummary.getVersion()) );
			}

			if ( newValues.size() > 0 ) { 
				
				if (!networkDao.networkIsValid(networkUUID))
					throw new InvalidNetworkException();

				try {
					if ( networkDao.networkIsLocked(networkUUID,10)) {
						throw new NetworkConcurrentModificationException ();
					}
				} catch (InterruptedException e2) {
					e2.printStackTrace();
					throw new NdexException("Failed to check network lock: " + e2.getMessage());
				} 
				
				
				try {
					networkDao.lockNetwork(networkUUID);
				
					networkDao.updateNetworkProfile(networkUUID, newValues);
					
					// recreate files and update db
					updateNetworkAttributesAspect(networkDao, networkUUID);

					networkDao.unlockNetwork(networkUUID);
					
					// update the solr Index 
					NetworkIndexLevel lvl = networkDao.getIndexLevel(networkUUID);
					if ( lvl != NetworkIndexLevel.NONE) {
					  networkDao.setFlag(networkUUID, "iscomplete", false);
					  networkDao.commit();
					  NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,SolrIndexScope.global,false,null,lvl, false));
					} else {
						  networkDao.setFlag(networkUUID, "iscomplete", true);
						  networkDao.commit();
					}
				} catch ( SQLException | IOException | IllegalArgumentException |NdexException e ) {
					networkDao.rollback();
					try {
						networkDao.unlockNetwork(networkUUID);
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					throw e;
				}
				
			}
		}  
	}

	
	/**
	 * 
	 * @param pathPrefix path of the directory that has the CxAttributeDeclaration aspect file. 
	 * It should end with "/"
	 * @return the declaration object. null if network has no attributes declared.
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	protected static CxAttributeDeclaration getAttrDeclarations(String pathPrefix) throws JsonParseException, JsonMappingException, IOException {			
		File attrDeclF = new File ( pathPrefix + CxAttributeDeclaration.ASPECT_NAME);
		CxAttributeDeclaration[] declarations = null;
		if ( attrDeclF.exists() ) {
			ObjectMapper om = new ObjectMapper();
			declarations = om.readValue(attrDeclF, CxAttributeDeclaration[].class);
		}

		if ( declarations != null)
			return declarations[0];
		
		return null;
	}
	
	// update the networkAttributes aspect file and also update the metadata in the db.
	protected static void updateNetworkAttributesAspect(NetworkDAO networkDao, UUID networkUUID) throws JsonParseException, JsonMappingException, SQLException, IOException, NdexException {
		NetworkSummary fullSummary = networkDao.getNetworkSummaryById(networkUUID);
		String fileStoreDir = Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID.toString() + "/";
		String aspectFilePath = fileStoreDir + CXNetworkLoader.CX1AspectDir + "/" + NetworkAttributesElement.ASPECT_NAME;
		String cx2AspectDirPath = fileStoreDir + CX2NetworkLoader.cx2AspectDirName + "/";
		
		boolean isSingleNetwork = fullSummary.getSubnetworkIds().isEmpty();
		//update the networkAttributes aspect in cx and cx2 
		List<NetworkAttributesElement> attrs = getNetworkAttributeAspectsFromSummary(fullSummary);
		
		CxAttributeDeclaration networkAttrDecl = null;
		if ( attrs.size() > 0 ) {	
			AspectAttributeStat attributeStats = new AspectAttributeStat();
			CxNetworkAttribute cx2NetAttr = new CxNetworkAttribute();

			// write cx network attribute aspect file.
			try (CXAspectWriter writer = new CXAspectWriter(aspectFilePath) ) {
				for ( NetworkAttributesElement e : attrs) {
					writer.writeCXElement(e);	
					writer.flush();

					if ( isSingleNetwork) {
						attributeStats.addNetworkAttribute(e);
					    List<String> warnings = new ArrayList<>(); 
						Object attrValue = AttributeDeclaredAspect.convertAttributeValue(e, warnings);
						if (!warnings.isEmpty())
						  throw new NdexException("Value of attribute " + e.getName() + " is "+  e.getValue() + ", which is not a supported double value in CX2." );
						
						Object oldV = cx2NetAttr.getAttributes().put(e.getName(), attrValue);
						if ( oldV !=null)
							throw new NdexException("Duplicated network attribute name found: " + e.getName());
					}
				}
			}
			
			// write cx2 network attribute aspect file
			if ( isSingleNetwork) {
				networkAttrDecl = attributeStats.createCxDeclaration();
			
				try (CX2AspectWriter<CxNetworkAttribute> aspWtr = new CX2AspectWriter<>(cx2AspectDirPath + CxNetworkAttribute.ASPECT_NAME)) {
					aspWtr.writeCXElement(cx2NetAttr);
				}
			}
		} else { // remove the aspect file if it exists
			File f = new File ( aspectFilePath);
			if ( f.exists())
				f.delete();
			if ( isSingleNetwork) {
				f = new File(cx2AspectDirPath + CxNetworkAttribute.ASPECT_NAME);
				if ( f.exists())
					f.delete();
			}
		}
		
				
		//update cx and cx2 metadata for networkAttributes
		MetaDataCollection metadata = networkDao.getMetaDataCollection(networkUUID);
		
		List<CxMetadata>   cx2metadata = networkDao.getCx2MetaDataList(networkUUID);
		
		if ( attrs.size() == 0 ) {
			metadata.remove(NetworkAttributesElement.ASPECT_NAME);
			if ( isSingleNetwork)
				cx2metadata.removeIf( n -> n.getName().equals(CxNetworkAttribute.ASPECT_NAME));
		} else {
			MetaDataElement elmt = metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME);
			if ( elmt == null) {
				elmt = new MetaDataElement(NetworkAttributesElement.ASPECT_NAME, "1.0");
				metadata.add(elmt);
			}
			elmt.setElementCount(Long.valueOf(attrs.size()));
						
			if ( isSingleNetwork  && ! cx2metadata.stream().anyMatch(
					(CxMetadata n) -> n.getName().equals(CxNetworkAttribute.ASPECT_NAME) )) {
			
				CxMetadata m = new CxMetadata(CxNetworkAttribute.ASPECT_NAME, 1);
				cx2metadata.add(m);
			}
		}
		networkDao.updateMetadataColleciton(networkUUID, metadata);

		
		// update attribute declaration aspect and cx2 metadata
		if ( isSingleNetwork) {
			CxAttributeDeclaration decls = getAttrDeclarations(cx2AspectDirPath);
			if (decls == null) {
				if (networkAttrDecl != null) { // has new attributes
					decls = new CxAttributeDeclaration();
					decls.add(CxNetworkAttribute.ASPECT_NAME,
							networkAttrDecl.getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME));
					cx2metadata.add(new CxMetadata(CxAttributeDeclaration.ASPECT_NAME, 1));
				}
			} else {
				if (networkAttrDecl != null) {
					decls.getDeclarations().put(CxNetworkAttribute.ASPECT_NAME,
							networkAttrDecl.getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME));
				} else {
					decls.removeAspectDeclaration(CxNetworkAttribute.ASPECT_NAME);
					cx2metadata.removeIf((CxMetadata m) -> m.getName().equals(CxAttributeDeclaration.ASPECT_NAME));
				}
			}

			networkDao.setCxMetadata(networkUUID, cx2metadata);
			try (CX2AspectWriter<CxAttributeDeclaration> aspWtr = new CX2AspectWriter<>(
					cx2AspectDirPath + CxAttributeDeclaration.ASPECT_NAME)) {
				aspWtr.writeCXElement(decls);
			}

		}
		
		//Recreate the CX file 					
		CXNetworkFileGenerator g = new CXNetworkFileGenerator(networkUUID, metadata);
		long cxFileSize = g.reCreateCXFile();
        
		//Recreate cx2 file
		if(isSingleNetwork) {
			CX2NetworkFileGenerator g2 = new CX2NetworkFileGenerator(networkUUID, cx2metadata);
			String tmpFilePath = g2.createCX2File();
			java.nio.file.Path cx2Path = Paths.get(fileStoreDir + CX2NetworkLoader.cx2NetworkFileName);
			Files.move(Paths.get(tmpFilePath), cx2Path, StandardCopyOption.ATOMIC_MOVE);
			long cx2FileSize = Files.size(cx2Path);
			networkDao.setNetworkFileSizes(networkUUID, cxFileSize, cx2FileSize);
		}
	}

	@PUT
	@Path("/{networkid}/summary")
	@Produces("application/json")
	public void updateNetworkSummary(
			@PathParam("networkid") final String networkId,
			final NetworkSummary summary
			)
            throws  NdexException, SQLException , IOException, IllegalArgumentException 
    {
		
		try (NetworkDAO networkDao = new NetworkDAO()){

			User user = getLoggedInUser();
			UUID networkUUID = UUID.fromString(networkId);
	
	  	    if(networkDao.isReadOnly(networkUUID)) {
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !networkDao.isWriteable(networkUUID, user.getExternalId())) {
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if (!networkDao.networkIsValid(networkUUID))
				throw new InvalidNetworkException();

			try {
					if ( networkDao.networkIsLocked(networkUUID,10)) {
						throw new NetworkConcurrentModificationException ();
					}
			} catch (InterruptedException e2) {
					e2.printStackTrace();
					throw new NdexException("Failed to check network lock: " + e2.getMessage());
			} 
				
				
			try {
				networkDao.lockNetwork(networkUUID);
				
				networkDao.updateNetworkSummary(networkUUID, summary);
				
				//recreate files and update db
				updateNetworkAttributesAspect(networkDao, networkUUID);
					
				networkDao.unlockNetwork(networkUUID);
					
				// update the solr Index
				NetworkIndexLevel lvl = networkDao.getIndexLevel(networkUUID);
				if ( lvl != NetworkIndexLevel.NONE) {
						  networkDao.setFlag(networkUUID, "iscomplete", false);
						  networkDao.commit();
						  NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,SolrIndexScope.global,false,null,lvl,false));
				} else {
						  networkDao.setFlag(networkUUID, "iscomplete", true);
						  networkDao.commit();
				}
			} catch ( SQLException | IOException | IllegalArgumentException |NdexException e ) {
					networkDao.rollback();
					try {
						networkDao.unlockNetwork(networkUUID);
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					throw e;
			}
				
		}
		
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
		
		try (UserDAO dao = new UserDAO()) {
			   dao.checkDiskSpace(getLoggedInUserId());
		}
    	
        UUID networkId = UUID.fromString(networkIdStr);

   //     String ownerAccName = null;
        try ( NetworkDAO daoNew = new NetworkDAO() ) {
           User user = getLoggedInUser();
           
         try {
	  	   if( daoNew.isReadOnly(networkId)) {
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !daoNew.isWriteable(networkId, user.getExternalId())) {
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if ( daoNew.networkIsLocked(networkId)) {
				daoNew.close();
				throw new NetworkConcurrentModificationException ();
			} 
			
			daoNew.lockNetwork(networkId);
						
	        UUID tmpNetworkId = storeRawNetworkFromMultipart (input, cx1NetworkFileName);

	        updateNetworkFromSavedFile( networkId, daoNew, tmpNetworkId);
			
           } catch (SQLException | NdexException | IOException e) {
        	   e.printStackTrace();
        	   daoNew.rollback();
        	   daoNew.unlockNetwork(networkId);  

        	   throw e;
           } 
			
        }  
    	      
	     NdexServerQueue.INSTANCE.addSystemTask(new CXNetworkLoadingTask(networkId, /* ownerAccName,*/ true, visibility,extraIndexOnNodes));
    }


    @PUT
    @Path("/{networkid}")
	@Operation(summary = "Update a Network", description = "Update the specified network with new content, based on CX data.")
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

   //     String ownerAccName = null;
        try ( NetworkDAO daoNew = lockNetworkForUpdate(networkId) ) {
           	
			try (InputStream in = this.getInputStreamFromRequest()) {
					UUID tmpNetworkId = storeRawNetworkFromStream(in, cx1NetworkFileName);

					updateNetworkFromSavedFile(networkId, daoNew, tmpNetworkId);				
			
           } catch (SQLException | NdexException | IOException e) {
        	   daoNew.rollback();
        	   daoNew.unlockNetwork(networkId);  

        	   throw e;
           } 
			
			
        }  
    	      
	     NdexServerQueue.INSTANCE.addSystemTask(new CXNetworkLoadingTask(networkId, /* ownerAccName,*/ true, visibility,extraIndexOnNodes));
	    // return networkIdStr; 
    }



	private static void updateNetworkFromSavedFile(UUID networkId, NetworkDAO daoNew,
			UUID tmpNetworkId) throws SQLException, NdexException, IOException, JsonParseException,
			JsonMappingException, ObjectNotFoundException {
		String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId.toString()
				+ "/network.cx";
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
    
    
    /**
     * This function updates aspects of a network. The payload is a CX document which contains the aspects (and their metadata that we will use
     *  to update the network). If an aspect only has metadata but no actual data, an Exception will be thrown.
     * @param networkIdStr
     * @param input
     * @throws Exception
     */
    @PUT
    @Path("/{networkid}/aspects")
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public void updateCXNetworkAspects(final @PathParam("networkid") String networkIdStr,
    		MultipartFormDataInput input) throws Exception 
    {
    	
    	try (UserDAO dao = new UserDAO()) {
			   dao.checkDiskSpace(getLoggedInUserId());
		}
    	
        UUID networkId = UUID.fromString(networkIdStr);

        try ( NetworkDAO daoNew = new NetworkDAO() ) {
           User user = getLoggedInUser();
           
	  	   if( daoNew.isReadOnly(networkId)) {
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !daoNew.isWriteable(networkId, user.getExternalId())) {
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if ( daoNew.networkIsLocked(networkId)) {
				daoNew.close();
				throw new NetworkConcurrentModificationException ();
			} 
			
			daoNew.lockNetwork(networkId);
			
			
	        UUID tmpNetworkId = storeRawNetworkFromMultipart (input, cx1NetworkFileName); //network stored as a temp network
	        
	    	updateNetworkFromSavedAspects(networkId, daoNew, tmpNetworkId);
			
           } 
    }
    
    @PUT
    @Path("/{networkid}/aspects")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/json")
    public void updateAspectsJson(final @PathParam("networkid") String networkIdStr
    		) throws Exception 
    {
    	
    	try (UserDAO dao = new UserDAO()) {
			   dao.checkDiskSpace(getLoggedInUserId());
		}
    	
        UUID networkId = UUID.fromString(networkIdStr);

     //   String ownerAccName = null;
        try ( NetworkDAO daoNew = new NetworkDAO() ) {
           User user = getLoggedInUser();
           
	  	   if( daoNew.isReadOnly(networkId)) {
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !daoNew.isWriteable(networkId, user.getExternalId())) {
		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if ( daoNew.networkIsLocked(networkId)) {
				daoNew.close();
				throw new NetworkConcurrentModificationException ();
			} 
			
			daoNew.lockNetwork(networkId);
			
			try (InputStream in = this.getInputStreamFromRequest()) {

	        UUID tmpNetworkId = storeRawNetworkFromStream(in, cx1NetworkFileName); //network stored as a temp network
	        
	    	updateNetworkFromSavedAspects( networkId, daoNew, tmpNetworkId);
			}
           } 
    }



	private static void updateNetworkFromSavedAspects( UUID networkId, NetworkDAO daoNew,
			UUID tmpNetworkId) throws SQLException, IOException {
		try ( CXNetworkAspectsUpdater aspectUpdater = new CXNetworkAspectsUpdater(networkId, /*ownerAccName,*/daoNew, tmpNetworkId) ) {
			
			aspectUpdater.update();

		} catch ( IOException | NdexException | SQLException | RuntimeException | SolrServerException e1) {
				logger.error("Error occurred when updating aspects of network " + networkId + ": " + e1.getMessage());
				e1.printStackTrace();
				daoNew.setErrorMessage(networkId, e1.getMessage());
				daoNew.unlockNetwork(networkId);   				    		
		} 
		     
		FileUtils.deleteDirectory(new File(Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId.toString()));
		 
		daoNew.unlockNetwork(networkId);
	}
    
	@DELETE
	@Path("/{networkid}")
	@Produces("application/json")

	public void deleteNetwork(final @PathParam("networkid") String id) throws NdexException, SQLException {
		    
		try (NetworkDAO networkDao = new NetworkDAO()) {
			UUID networkId = UUID.fromString(id);
			UUID userId = getLoggedInUser().getExternalId();
			if(networkDao.isAdmin(networkId, userId) ) {
				if (!networkDao.isReadOnly(networkId) ) {
					if ( !networkDao.networkIsLocked(networkId)) {
					/*
						NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager();
						globalIdx.deleteNetwork(id);
						try (SingleNetworkSolrIdxManager idxManager = new SingleNetworkSolrIdxManager(id)) {
							idxManager.dropIndex();
						}	
					*/	
						networkDao.deleteNetwork(UUID.fromString(id), getLoggedInUser().getExternalId());
						networkDao.commit();

						// move the row network to archive folder and delete the folder
					    String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkId.toString();
				        /*String archivePath = Configuration.getInstance().getNdexRoot() + "/data/_archive/";
				        
				        File archiveDir = new File(archivePath);
				        if (!archiveDir.exists())
				        		archiveDir.mkdir();
				        
				        
				        java.nio.file.Path src = Paths.get(pathPrefix+ "/network.cx");     
						java.nio.file.Path tgt = Paths.get(archivePath + "/" + networkId.toString() + ".cx");
						*/
						try {
						//	Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE); 	
						
							FileUtils.deleteDirectory(new File(pathPrefix));
						} catch (IOException e) {
							e.printStackTrace();
							throw new NdexException("Failed to delete directory. Error: " + e.getMessage());
						}
						
						NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteNetwork(networkId));
				
						return;
					}
					throw new NetworkConcurrentModificationException ();
				}
				  throw new NdexException("Can't delete a read-only network.");
			}
			//TODO: need to check if the network actually exists and give an 404 error for that case.
			throw new NdexException("Only network owner can delete a network.");	
		} catch ( IOException e ) {
			throw new NdexException ("Error occurred when deleting network: " + e.getMessage(), e);
		}
			
	}
	

	/* *************************************************************************
	 * Saves an uploaded network file. Determines the type of file uploaded,
	 * saves the file, and creates a task.
	 *
	 *  Removing it. No longer relevent in v2.
	 * @param uploadedNetwork
	 *            The uploaded network file.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to parse the file, or create the network in the
	 *             database.
	 * @throws IOException 
	 * @throws SQLException 
	 **************************************************************************/
	/*
	 * refactored to support non-transactional database operations
	 */
/*	@POST
	@Path("/upload")
	@Consumes("multipart/form-data")
	@Produces("application/json")
	public Task uploadNetwork( MultipartFormDataInput input) 
                  //@MultipartForm UploadedFile uploadedNetwork)
			throws IllegalArgumentException, SecurityException, NdexException, IOException, SQLException {


		 try (UserDAO dao = new UserDAO()) {
			   dao.checkDiskSpace(getLoggedInUserId());
		 }
		  
		Map<String, List<InputPart>> uploadForm = input.getFormDataMap();

		List<InputPart> foo = uploadForm.get("filename");
		
		String fname = "";
		for (InputPart inputPart : foo)
	       {
	               // convert the uploaded file to inputstream and write it to disk
	               fname += inputPart.getBodyAsString();
	      }

		if (fname.length() <1) {
			throw new NdexException ("");
		}
		String ext = FilenameUtils.getExtension(fname).toLowerCase();

		if ( !ext.equals("sif") && !ext.equals("xbel") && !ext.equals("xgmml") && !ext.equals("owl") && !ext.equals("cx")
				&& !ext.equals("xls") && ! ext.equals("xlsx")) {
			throw new NdexException(
					"The uploaded file type is not supported; must be Excel, XGMML, SIF, BioPAX, cx or XBEL.");
		}
		
		UUID taskId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();

		final File uploadedNetworkPath = new File(Configuration.getInstance().getNdexRoot() +
				"/uploaded-networks");
		if (!uploadedNetworkPath.exists())
			uploadedNetworkPath.mkdir();

		String fileFullPath = uploadedNetworkPath.getAbsolutePath() + "/" + taskId + "." + ext;

	       //Get file data to save
	    List<InputPart> inputParts = uploadForm.get("fileUpload");

		
		final File uploadedNetworkFile = new File(fileFullPath);

		if (!uploadedNetworkFile.exists())
			try {
				uploadedNetworkFile.createNewFile();
			} catch (IOException e1) {
				throw new NdexException ("Failed to create file " + fileFullPath + " on server when uploading " + 
						fname + ": " + e1.getMessage());				
			}

        byte[] bytes = new byte[2048];
		for (InputPart inputPart : inputParts)
	       {
	               
	               // convert the uploaded file to inputstream and write it to disk
	               InputStream inputStream = inputPart.getBody(InputStream.class, null);
	              
	               OutputStream out = new FileOutputStream(new File(fileFullPath));

	               int read = 0;
	               while ((read = inputStream.read(bytes)) != -1) {
	                  out.write(bytes, 0, read);
	               }
	               inputStream.close();
	               out.flush();
	               out.close();
	 
	      }	

		Task processNetworkTask = new Task();
		processNetworkTask.setExternalId(taskId);
		processNetworkTask.setDescription(fname); //uploadedNetwork.getFilename());
		processNetworkTask.setTaskType(TaskType.PROCESS_UPLOADED_NETWORK);
		processNetworkTask.setPriority(Priority.LOW);
		processNetworkTask.setProgress(0);
		processNetworkTask.setResource(fileFullPath);
		processNetworkTask.setStatus(Status.QUEUED);
		processNetworkTask.setTaskOwnerId(this.getLoggedInUser().getExternalId());

		try (TaskDAO dao = new TaskDAO()){
			dao.createTask(processNetworkTask);
			dao.commit();		
			
		} catch (IllegalArgumentException iae) {
			logger.error("[end: Exception caught:]{}", iae);
			throw iae;
		} catch (Exception e) {
			logger.error("[end: Exception caught:]{}", e);
			throw new NdexException(e.getMessage());
		}

		logger.info("[end: Uploading network file. Task for uploading network is created.]");
		return processNetworkTask;
	}

*/

	@PUT
	@Path("/{networkid}/systemproperty")
	@Produces("application/json")
	public void setNetworkFlag(
			@PathParam("networkid") final String networkIdStr,
			final Map<String,Object> parameters)

			throws IllegalArgumentException, NdexException, SQLException, IOException {
		
			accLogger.info("[data]\t[" + parameters.entrySet()
	        .stream()
	        .map(entry -> entry.getKey() + ":" + entry.getValue()).reduce(null, ((r,e) -> {String rr = r==null? e:r+"," +e; return rr;}))
					 + "]" );		

		
			try (NetworkDAO networkDao = new NetworkDAO()) {
				UUID networkId = UUID.fromString(networkIdStr);
				if ( networkDao.hasDOI(networkId)) {
					if ( parameters.size() >1 || !parameters.containsKey("showcase"))
						throw new ForbiddenOperationException("Network with DOI can't be modified.");	
				}	
				UUID userId = getLoggedInUser().getExternalId();
					if ( !networkDao.networkIsValid(networkId))
						throw new InvalidNetworkException();
					
					if ( parameters.containsKey(readOnlyParameter)) {
						if (!networkDao.isAdmin(networkId, userId))
							throw new UnauthorizedOperationException("Only network owner can set readOnly Parameter.");
						boolean bv = ((Boolean)parameters.get(readOnlyParameter)).booleanValue();
						networkDao.setFlag(networkId, "readonly",bv);	 
					}
					if ( parameters.containsKey("visibility")) {
						if (!networkDao.isAdmin(networkId, userId))
							throw new UnauthorizedOperationException("Only network owner can set visibility Parameter.");
						
						VisibilityType visType = VisibilityType.valueOf((String)parameters.get("visibility"));
						networkDao.updateNetworkVisibility(networkId, visType, false);
						if ( !parameters.containsKey("index_level")) {
							NetworkIndexLevel lvl = networkDao.getIndexLevel(networkId);
							networkDao.commit();
							if ( lvl != NetworkIndexLevel.NONE) {
								NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,null,lvl,false));
							} 
						}
					}
					/*if ( parameters.containsKey("index")) {
						boolean bv = ((Boolean)parameters.get("index")).booleanValue();
						networkDao.setFlag(networkId, "solr_indexed",bv);	 
						if (bv) {
							networkDao.setFlag(networkId, "iscomplete",false);	 				
							networkDao.commit();
							NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,null));
						} else
							NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteNetwork(networkId, true)); //delete the entry from global idx.
														
					}*/
					if ( parameters.containsKey("index_level")) {
						NetworkIndexLevel lvl = parameters.get("index_level") == null? 
								NetworkIndexLevel.NONE : 
								NetworkIndexLevel.valueOf((String)parameters.get("index_level"));
						networkDao.setIndexLevel(networkId, lvl);	 
						if (lvl !=NetworkIndexLevel.NONE) {
							networkDao.setFlag(networkId, "iscomplete",false);	 				
							networkDao.commit();
							NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,null,lvl,true));
						} else
							NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteNetwork(networkId, true)); //delete the entry from global idx.
														
					}
					if ( parameters.containsKey("showcase")) {
						boolean bv = ((Boolean)parameters.get("showcase")).booleanValue();
						networkDao.setShowcaseFlag(networkId, userId, bv);
							
					}
				    networkDao.commit();
					return;
			}
		    
	}

	
	

	
	   @POST
	//	@PermitAll

	   @Path("")
	   @Operation(summary = "Create a CX Network", description = "Create a network from CX data.")
	   @Produces("text/plain")
	   @Consumes("multipart/form-data")
	   public Response createCXNetwork( MultipartFormDataInput input,
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
		   
		   
		   UUID uuid = storeRawNetworkFromMultipart ( input, cx1NetworkFileName);
		   return processRawNetwork(visibility, extraIndexOnNodes, uuid);

	   	}



	private Response processRawNetwork(VisibilityType visibility, Set<String> extraIndexOnNodes, UUID uuid)
			throws SQLException, NdexException, IOException, ObjectNotFoundException, JsonProcessingException,
			URISyntaxException {
		String uuidStr = uuid.toString();
		   accLogger.info("[data]\t[uuid:" +uuidStr + "]" );
	   
		   String urlStr = Configuration.getInstance().getHostURI()  + 
		            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ uuidStr;
		   
		   String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/" + cx1NetworkFileName;
		   long fileSize = new File(cxFileName).length();

		   // create entry in db. 
	       try (NetworkDAO dao = new NetworkDAO()) {
	    	  // NetworkSummary summary = 
	    			   dao.CreateEmptyNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName(), fileSize,null,null);
       
				dao.commit();
	       }
	       
	       NdexServerQueue.INSTANCE.addSystemTask(new CXNetworkLoadingTask(uuid, /*getLoggedInUser().getUserName(),*/ false, visibility, extraIndexOnNodes));		   
		   URI l = new URI (urlStr);

		   return Response.created(l).entity(l).build();
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
			   UUID uuid = storeRawNetworkFromStream(in, cx1NetworkFileName);
			   return processRawNetwork(visibility, extraIndexOnNodes, uuid);

		   }		   

	   	}
	   
	   
/*	   private static UUID storeRawNetworkFromStream(InputStream in) throws IOException {
		   
		   UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		   String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuid.toString();
		   
		   //Create dir
		   java.nio.file.Path dir = Paths.get(pathPrefix);
		   Set<PosixFilePermission> perms =
				    PosixFilePermissions.fromString("rwxrwxr-x");
				FileAttribute<Set<PosixFilePermission>> attr =
				    PosixFilePermissions.asFileAttribute(perms);
		   Files.createDirectory(dir,attr);
		   
		   //write content to file
		   String cxFilePath = pathPrefix + "/network.cx";
		   
		   try (OutputStream outputStream = new FileOutputStream(cxFilePath)) {
			   IOUtils.copy(in, outputStream);
			   outputStream.close();
		   } 
		   return uuid;
	   }
*/	   
	   
	 /*  private static UUID storeRawNetwork (MultipartFormDataInput input) throws IOException, BadRequestException {
		   Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
	       			       
		   //Get file data to save
		   List<InputPart> inputParts = uploadForm.get("CXNetworkStream");
		   if (inputParts == null)
			   throw new BadRequestException("Field CXNetworkStream is not found in the POSTed Data.");
			 
		   byte[] bytes = new byte[8192];
		   UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		   String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuid.toString();
				   
		   //Create dir
		   java.nio.file.Path dir = Paths.get(pathPrefix);
		   Set<PosixFilePermission> perms =
						    PosixFilePermissions.fromString("rwxrwxr-x");
		   FileAttribute<Set<PosixFilePermission>> attr =
						    PosixFilePermissions.asFileAttribute(perms);
		   Files.createDirectory(dir,attr);
				   
		   //write content to file
		   String cxFilePath = pathPrefix + "/network.cx";
		   try (FileOutputStream out = new FileOutputStream (cxFilePath ) ){     
			   for (InputPart inputPart : inputParts) {
				     // convert the uploaded file to inputstream and write it to disk
				   org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl p =
				        	(org.jboss.resteasy.plugins.providers.multipart.MultipartInputImpl.PartImpl) inputPart;
				   try (InputStream inputStream = p.getBody()) {
				              
				       int read = 0;
				       while ((read = inputStream.read(bytes)) != -1) {
				            out.write(bytes, 0, read);
				       }
				   }
				               
			   }
		   }
		   return uuid; 
	   }
	   */
	 
	   
	   private static List<NetworkAttributesElement> getNetworkAttributeAspectsFromSummary(NetworkSummary summary) 
			   throws JsonParseException, JsonMappingException, IOException {
			List<NetworkAttributesElement> result = new ArrayList<>();
			if ( summary.getName() !=null) 
				result.add(new NetworkAttributesElement(null, NdexClasses.Network_P_name, summary.getName()));
			if ( summary.getDescription() != null)
				result.add(new NetworkAttributesElement(null, NdexClasses.Network_P_desc, summary.getDescription()));
			if ( summary.getVersion() !=null)
				result.add(new NetworkAttributesElement(null, NdexClasses.Network_P_version, summary.getVersion()));
			
			if ( summary.getProperties() != null) {
				for ( NdexPropertyValuePair p : summary.getProperties()) {
					result.add(NetworkAttributesElement.createInstanceWithJsonValue(p.getSubNetworkId(), p.getPredicateString(),
							p.getValue(), ATTRIBUTE_DATA_TYPE.fromCxLabel(p.getDataType())));
				}
			}
			return result;
		}
	   
	   
	   @POST
	   @Path("/{networkid}/copy")
	   @Operation(summary = "Clone a Network", description = "This function clones the network specified by networkid to the signed in user’s account. It returns the URL of cloned network to the client.")
	   @Produces("text/plain")
	   public Response cloneNetwork( @PathParam("networkid") final String srcNetworkUUIDStr) throws Exception
	   {
	   
		   try (UserDAO dao = new UserDAO()) {
			   dao.checkDiskSpace(getLoggedInUserId());
		   }
		   
		   UUID srcNetUUID = UUID.fromString(srcNetworkUUIDStr);
		   
		   try ( NetworkDAO dao = new NetworkDAO ()) {
			   if ( ! dao.isReadable(srcNetUUID, getLoggedInUserId()) ) 
	                throw new UnauthorizedOperationException("User doesn't have read access to this network.");
	    		
			   if (!dao.networkIsValid(srcNetUUID)) {
				   throw new NdexException ("Invalid networks can not be copied.");
			   }
		   }
		   
		   UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		   String uuidStr = uuid.toString();
		   
		//	java.nio.file.Path src = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID.toString());
			java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr);
			
			//Create dir
			Set<PosixFilePermission> perms =
					    PosixFilePermissions.fromString("rwxrwxr-x");
			FileAttribute<Set<PosixFilePermission>> attr =
					    PosixFilePermissions.asFileAttribute(perms);
			Files.createDirectory(tgt,attr);
			
			String srcPathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID.toString() + "/";
			String tgtPathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/";

		    File srcAspectDir = new File ( srcPathPrefix + CXNetworkLoader.CX1AspectDir);
		    if ( srcAspectDir.exists()) {
		    	File tgtAspectDir = new File ( tgtPathPrefix + CXNetworkLoader.CX1AspectDir);
		    	FileUtils.copyDirectory(srcAspectDir, tgtAspectDir);
		    }
		    
		    //copy the cx2 aspect directories
		    String tgtCX2AspectPathPrefix = tgtPathPrefix + CX2NetworkLoader.cx2AspectDirName;
		    String srcCX2AspectPathPrefix = srcPathPrefix + CX2NetworkLoader.cx2AspectDirName;
		    File srcCX2AspectDir = new File (srcCX2AspectPathPrefix );
		    Files.createDirectories(Paths.get(tgtCX2AspectPathPrefix), attr);
		    for ( String fname : srcCX2AspectDir.list() ) {
		    	java.nio.file.Path src = Paths.get(srcPathPrefix + CX2NetworkLoader.cx2AspectDirName , fname);
		    	if (Files.isSymbolicLink(src)) {
		    		java.nio.file.Path link = Paths.get(tgtCX2AspectPathPrefix, fname);
		    		java.nio.file.Path target = Paths.get(tgtPathPrefix + CXNetworkLoader.CX1AspectDir, fname);
		    		Files.createSymbolicLink(link, target);
		    	} else {
		    		Files.copy(Paths.get(srcCX2AspectPathPrefix, fname),
		    				Paths.get(tgtCX2AspectPathPrefix, fname));
		    	}
		    	
		    }
		    
		    String urlStr = Configuration.getInstance().getHostURI()  + 
		            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ uuidStr;
		   // ProvenanceEntity entity = new ProvenanceEntity();
		  //  entity.setUri(urlStr + "/summary");
		   
		   String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID.toString() + "/" + cx1NetworkFileName;
		   long fileSize = new File(cxFileName).length();

		   // copy sample 
		   java.nio.file.Path srcSample = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + srcNetUUID.toString() + "/sample.cx");
		   if ( Files.exists(srcSample, LinkOption.NOFOLLOW_LINKS)) {
			   java.nio.file.Path tgtSample = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/sample.cx");
			   Files.copy(srcSample, tgtSample);
		   }
		   
		   
		   //TODO: generate prov:wasDerivedFrom and prov:wasGeneratedby field in both db record and the recreated CX file.
		   // Need to handle the case when this network was a clone (means it already has prov:wasGeneratedBy and wasDerivedFrom attributes
		   // create entry in db. 
	       try (NetworkDAO dao = new NetworkDAO()) {
	    //	   NetworkSummary summary = 
	    			   dao.CreateCloneNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName(), fileSize, srcNetUUID);

				CXNetworkFileGenerator g = new CXNetworkFileGenerator(uuid, dao /*, new Provenance(copyProv)*/);
				g.reCreateCXFile();
				CX2NetworkFileGenerator g2 = new CX2NetworkFileGenerator(uuid, dao);
				String tmpFilePath = g2.createCX2File();
				Files.move(Paths.get(tmpFilePath), 
						Paths.get(tgtPathPrefix + CX2NetworkLoader.cx2NetworkFileName), 
						StandardCopyOption.ATOMIC_MOVE); 				

				dao.setFlag(uuid, "iscomplete", true);
				dao.commit();
	       }
	       
			NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(uuid, SolrIndexScope.individual,true,null, NetworkIndexLevel.NONE,false));
	       			   
		   URI l = new URI (urlStr);

		   return Response.created(l).entity(l).build();

	   	}
		  

	    @POST
		@PermitAll
		@Path("/properties/score")
		@Produces("application/json")
	    public static int getScoresFromProperties(
	    		final List<NdexPropertyValuePair> properties)
	    		throws Exception {

			return Util.getNetworkScores (properties, true);
	    }
	  
	    @POST
		@PermitAll
		@Path("/summary/score")
		@Produces("application/json")
	    public static int getScoresFromNetworkSummary(
	    		final NetworkSummary summary)
	    		throws Exception {

			return Util.getNdexScoreFromSummary(summary);
	    }
	   	
	   	
}
