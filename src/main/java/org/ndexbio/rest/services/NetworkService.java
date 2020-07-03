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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.core.CXAspectWriter;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.model.exceptions.InvalidNetworkException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.NetworkConcurrentModificationException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.NetworkExportRequest;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Priority;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.FileFormat;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.task.CXNetworkLoadingTask;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.NetworkExportTask;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskDeleteNetwork;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Path("/network")
public class NetworkService extends NdexService {
	
	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);
	

	public NetworkService(@Context HttpServletRequest httpRequest) {
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

	@Deprecated
	public ProvenanceEntity getProvenance(
			@PathParam("networkid") final String networkId)

			throws IllegalArgumentException, JsonParseException, JsonMappingException, IOException, NdexException, SQLException {
				
		try (NetworkDAO daoNew = new NetworkDAO()) {
			if ( !daoNew.isReadable(UUID.fromString(networkId), getLoggedInUserId()) )
				throw new UnauthorizedOperationException("Network " + networkId + " is not readable to this user.");

			return daoNew.getProvenance(UUID.fromString(networkId));

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

    @Deprecated
    public ProvenanceEntity setProvenance(@PathParam("networkid")final String networkIdStr, final ProvenanceEntity provenance)
    		throws Exception {
    
		User user = getLoggedInUser();
    	NetworkServiceV2.setProvenance_aux(networkIdStr, provenance, user);
    	return provenance;
		
    }



    /**************************************************************************
    * Sets network properties.
     * @throws Exception
    *
    **************************************************************************/
    @PUT
	@Path("/{networkid}/properties")
	@Produces("application/json")
    @ApiDoc("Updates the 'properties' field of the network specified by 'networkId' to be the list of " +
            "NdexPropertyValuePair  objects in the PUT data.")
    public int setNetworkProperties(
    		@PathParam("networkid")final String networkId,
    		final List<NdexPropertyValuePair> properties)
    		throws Exception {

		try (NetworkDAO daoNew = new NetworkDAO()) {
			
			User user = getLoggedInUser();
			UUID networkUUID = UUID.fromString(networkId);
			
	  	    if(daoNew.isReadOnly(networkUUID)) {
				throw new NdexException ("Can't update readonly network.");				
			} 
			
			if ( !daoNew.isWriteable(networkUUID, user.getExternalId())) {

		        throw new UnauthorizedOperationException("User doesn't have write permissions for this network.");
			} 
			
			if ( daoNew.networkIsLocked(networkUUID)) {
				throw new NetworkConcurrentModificationException ();
			} 
			if ( !daoNew.networkIsValid(networkUUID))
				throw new InvalidNetworkException();
			
			int i = daoNew.setNetworkProperties(networkUUID, properties);

            //DW: Handle provenance

  /*          ProvenanceEntity oldProv = daoNew.getProvenance(networkUUID);
            ProvenanceEntity newProv = new ProvenanceEntity();
            newProv.setUri( oldProv.getUri() );

            NetworkSummary summary = daoNew.getNetworkSummaryById(networkUUID);
            Helper.populateProvenanceEntity(newProv, summary);
            ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.SET_NETWORK_PROPERTIES, summary.getModificationTime());
            List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
			eventProperties.add( new SimplePropertyValuePair("user name", user.getUserName()) ) ;

            for( NdexPropertyValuePair vp : properties )
            {
                SimplePropertyValuePair svp = new SimplePropertyValuePair(vp.getPredicateString(), vp.getValue());
                eventProperties.add(svp);
            }
            event.setProperties(eventProperties);
            List<ProvenanceEntity> oldProvList = new ArrayList<>();
            oldProvList.add(oldProv);
            event.setInputs(oldProvList);

            newProv.setCreationEvent(event);
            daoNew.setProvenance(networkUUID, newProv);  */

            NetworkSummary fullSummary = daoNew.getNetworkSummaryById(networkUUID);
			
			//update the networkProperty aspect 
			List<NetworkAttributesElement> attrs = getNetworkAttributeAspectsFromSummary(fullSummary);
			if ( attrs.size() > 0 ) {					
				try (CXAspectWriter writer = new CXAspectWriter(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/" 
						+ NetworkAttributesElement.ASPECT_NAME) ) {
					for ( NetworkAttributesElement e : attrs) {
						writer.writeCXElement(e);	
						writer.flush();
					}
				}
			}
			
			//update metadata
			MetaDataCollection metadata = daoNew.getMetaDataCollection(networkUUID);
			MetaDataElement elmt = metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME);
			if ( elmt == null) {
				elmt = new MetaDataElement();
			}
			elmt.setElementCount(Long.valueOf(attrs.size()));
			daoNew.updateMetadataColleciton(networkUUID, metadata);

			//Recreate the CX file 					
			CXNetworkFileGenerator g = new CXNetworkFileGenerator(networkUUID, /* fullSummary,*/ metadata /*,newProv*/);
			g.reCreateCXFile();
			
			daoNew.unlockNetwork(networkUUID);
            
			// update the solr Index
			NetworkIndexLevel idxLvl = daoNew.getIndexLevel(networkUUID);
			if ( idxLvl != NetworkIndexLevel.NONE) {
				daoNew.setFlag(networkUUID, "iscomplete", false);
				daoNew.commit();
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,SolrIndexScope.global,false,null,idxLvl,false));
			}	
   			return i;
		} catch (Exception e) {
			
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
	@Path("/{networkid}")
	@Produces("application/json")
	@ApiDoc("Retrieves a NetworkSummary object based on the network specified by 'networkId'. This " +
            "method returns an error if the network is not found or if the authenticated user does not have " +
            "READ permission for the network.")
	public NetworkSummary getNetworkSummary(
			@PathParam("networkid") final String networkIdStr)

			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
		
		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			UUID networkId = UUID.fromString(networkIdStr);
			if ( dao.isReadable(networkId, userId)) {
				NetworkSummary summary = dao.getNetworkSummaryById(networkId);

				return summary;
			}
				
			throw new ObjectNotFoundException ("network", networkId);
		}  
			
			
	}


	


	
	@PermitAll
	@GET
	@Path("/{networkid}/asCX")
	//TODO: handle cached network from hardDrive.
	public Response getCompleteNetworkAsCX(	@PathParam("networkid") final String networkId)
			throws IllegalArgumentException, NdexException, SQLException {
    	
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( ! dao.isReadable(UUID.fromString(networkId), getLoggedInUserId()))
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");

    	}
    	
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx";

    	try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
		//	setZipFlag();
		//	logger.info("[end: Return network {}]", networkId);
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
		} catch (IOException e) {
		//	logger.error("[end: Ndex server can't find file: {}]", e.getMessage());
			throw new NdexException ("Ndex server can't find file: " + e.getMessage());
		}
		
	}  

	@PermitAll
	@GET
	@Path("/{networkid}/sample")

	public Response getSampleNetworkAsCX(	@PathParam("networkid") final String networkId)
			throws IllegalArgumentException, NdexException, SQLException {
  	
    	try (NetworkDAO dao = new NetworkDAO()) {
    		if ( ! dao.isReadable(UUID.fromString(networkId), getLoggedInUserId()))
                throw new UnauthorizedOperationException("User doesn't have read access to this network.");

    	}
    	
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/sample.cx";
		
		try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
			//	setZipFlag();
			return 	Response.ok().type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
		} catch ( FileNotFoundException e) {
				throw new ObjectNotFoundException("Sample network of " + networkId + " not found");
		}  
		
	}  
	
	

/*	private class CXNetworkQueryWriterThread extends Thread {
		private OutputStream o;
		private String networkId;
		private CXSimplePathQuery parameters;
		
		public CXNetworkQueryWriterThread (OutputStream out, String  networkUUIDStr, CXSimplePathQuery query) {
			o = out;
			networkId = networkUUIDStr;
			this.parameters = query;
		}
		
		public void run() {
			try (CXNetworkExporter dao = new CXNetworkExporter (networkId) ) {
				    dao.exportSubnetworkInCX(o, parameters,true);   
			} catch (IOException e) {
					logger.error("IOException in CXNetworkWriterThread: " + e.getMessage());
					e.printStackTrace();
			} catch (NdexException e1) {
			     logger.error("Ndex error: " + e1.getMessage());
			     e1.printStackTrace();
			} catch (Exception e1) {
				logger.error("Ndex excption: " + e1.getMessage());
				e1.printStackTrace();
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					e.printStackTrace();
					logger.error("Failed to close outputstream in CXNetworkWriterThread.");
				}
			} 
		}
		
	} */


/*	private class CXAspectElementsWriterThread extends Thread {
		private OutputStream o;
		private String networkId;
		private String aspect;
		private int limit;
		public CXAspectElementsWriterThread (OutputStream out, String networkId, String aspectName, int limit) {
			o = out;
			this.networkId = networkId;
			aspect = aspectName;
			this.limit = limit;
		}
		
		public void run() {
			try (CXNetworkExporter dao = new CXNetworkExporter (networkId)) {
				    dao.writeOneAspectInCX(o, aspect, limit, true); 
			} catch (IOException e) {
					logger.error("IOException in CXAspectElementWriterThread: " + e.getMessage());
					e.printStackTrace();
			} catch (NdexException e1) {
			     logger.error("Ndex error: " + e1.getMessage());
			     e1.printStackTrace();
			} catch (Exception e1) {
				logger.error("Ndex exception: " + e1.getMessage());
				e1.printStackTrace();
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
		
	} */

	
/*	private class CXNetworkAspectsWriterThread extends Thread {
		private OutputStream o;
		private String networkId;
		private Set<String> aspects;
		
		public CXNetworkAspectsWriterThread (OutputStream out, String networkId, Set<String> aspectNames) {
			o = out;
			this.networkId = networkId;
			this.aspects = aspectNames;
		}
		
		public void run() {
		}
		
	} 
	private class CXNetworkLoadThread extends Thread {
		private UUID networkId;
		public CXNetworkLoadThread (UUID networkUUID  ) {
			this.networkId = networkUUID;
		}
		
		public void run() {
            
		}
	} */


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
	//@PermitAll
	@Path("/{networkid}/user/{permission}/{start}/{size}")
	@Produces("application/json")
    @ApiDoc("Retrieves a list of Membership objects which specify user permissions for the network specified by " +
            "'networkId'. The value of the 'permission' parameter constrains the type of the returned Membership " +
            "objects and may take the following set of values: READ, WRITE, and ADMIN.  READ, WRITE, and ADMIN are mutually exclusive. Memberships of all types can " +
            "be retrieved by permission = 'ALL'.   The maximum number of Membership objects to retrieve in the query " +
            "is set by 'blockSize' (which may be any number chosen by the user) while  'skipBlocks' specifies the " +
            "number of blocks that have already been read.")
	public List<Membership> getNetworkUserMemberships(@PathParam("networkid") final String networkIdStr,
			@PathParam("permission") final String permissions ,
			@PathParam("start") int skipBlocks,
			@PathParam("size") int blockSize) throws NdexException, SQLException {

		
		Permissions permission = null;
		if ( ! permissions.toUpperCase().equals("ALL")) {
			permission = Permissions.valueOf(permissions.toUpperCase());
		} 
		
		UUID networkId = UUID.fromString(networkIdStr);
		
		try (NetworkDAO networkDao = new NetworkDAO()) {
			if ( !networkDao.isAdmin(networkId, getLoggedInUserId())) 
				throw new UnauthorizedOperationException("Authenticated user is not the admin of this network");

			List<Membership> results = networkDao.getNetworkUserMemberships(
					networkId, permission, skipBlocks, blockSize);
			return results;
		} 
	}


	
	@POST
	@Path("/{networkid}/summary")
	@Produces("application/json")
	@ApiDoc("This method updates the profile information of the network specified by networkId based on a " +
	        "POSTed JSON object specifying the attributes to update. Any profile attributes specified will be " + 
	        "updated but attributes that are not specified will have no effect - omission of an attribute does " +
	        "not mean deletion of that attribute. The network profile attributes that can be updated by this " +
	        "method are: 'name', 'description', 'version'. visibility are no longer updated by this function. It is managed by setNetworkFlag function from 2.0")
	public void updateNetworkProfile(
			@PathParam("networkid") final String networkId,
			final NetworkSummary partialSummary
			)
            throws  NdexException, SQLException, IOException, IllegalArgumentException 
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
	    //    List<SimplePropertyValuePair> entityProperties = new ArrayList<>();

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
	//	            entityProperties.add( new SimplePropertyValuePair("version", partialSummary.getVersion()) );
			} 

			if ( newValues.size() > 0 ) { 
				
				if ( networkDao.networkIsLocked(networkUUID)) {
					throw new NetworkConcurrentModificationException ();
				} 
				
				if (!networkDao.networkIsValid(networkUUID))
					throw new InvalidNetworkException();
		
				try {
					networkDao.lockNetwork(networkUUID);
				
					networkDao.updateNetworkProfile(networkUUID, newValues);

					//DW: Handle provenance
					//Special Logic. Test whether we should record provenance at all.
					//If the only thing that has changed is the visibility, we should not add a provenance
					//event.
		

					//Treat all summary values that are null like ""
	//				String summaryName = partialSummary.getName() == null ? "" : partialSummary.getName().trim();
	//				String summaryDescription = partialSummary.getDescription() == null ? "" : partialSummary.getDescription().trim();
	//				String summaryVersion = partialSummary.getVersion() == null ? "" : partialSummary.getVersion().trim();

	/*				ProvenanceEntity newProv = new ProvenanceEntity();
					if( !oldName.equals(summaryName) || !oldDescription.equals(summaryDescription) || !oldVersion.equals(summaryVersion) )
					{
						if ( oldProv !=null )   //TODO: initialize the URI properly when there is null.
							newProv.setUri(oldProv.getUri());

						newProv.setProperties(entityProperties);

						ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.UPDATE_NETWORK_PROFILE, partialSummary.getModificationTime());

						List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
						eventProperties.add( new SimplePropertyValuePair("user name", this.getLoggedInUser().getUserName()) ) ;

						if (partialSummary.getName() != null)
							eventProperties.add(new SimplePropertyValuePair("dc:title", partialSummary.getName()));

						event.setProperties(eventProperties);
						List<ProvenanceEntity> oldProvList = new ArrayList<>();
						oldProvList.add(oldProv);
						event.setInputs(oldProvList);

						newProv.setCreationEvent(event);
						networkDao.setProvenance(networkUUID, newProv);
					} */
				
					NetworkSummary fullSummary = networkDao.getNetworkSummaryById(networkUUID);
					
					//update the networkProperty aspect 
					List<NetworkAttributesElement> attrs = getNetworkAttributeAspectsFromSummary(fullSummary);
					if ( attrs.size() > 0 ) {					
						try (CXAspectWriter writer = new CXAspectWriter(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/" 
								+ NetworkAttributesElement.ASPECT_NAME) ) {
							for ( NetworkAttributesElement e : attrs) {
								writer.writeCXElement(e);	
								writer.flush();
							}
						}
					}
					
					//update metadata
					MetaDataCollection metadata = networkDao.getMetaDataCollection(networkUUID);
					MetaDataElement elmt = metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME);
					if ( elmt == null) {
						elmt = new MetaDataElement();
					}
					elmt.setElementCount(Long.valueOf(attrs.size()));
					networkDao.updateMetadataColleciton(networkUUID, metadata);

					//Recreate the CX file 					
					CXNetworkFileGenerator g = new CXNetworkFileGenerator(networkUUID, /*fullSummary,*/ metadata /*, newProv*/);
					g.reCreateCXFile();
					
					networkDao.unlockNetwork(networkUUID);
					
					// update the solr Index
					NetworkIndexLevel idxLvl = networkDao.getIndexLevel(networkUUID);
					if ( idxLvl != NetworkIndexLevel.NONE) {
						networkDao.setFlag(networkUUID, "iscomplete", false);
						networkDao.commit();
						NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,SolrIndexScope.global,false,null,idxLvl,false));
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





	@POST
	@PermitAll
	@Path("/textsearch/{start}/{size}")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public NetworkSearchResult searchNetwork_solr(
			final SimpleNetworkQuery query,
			@PathParam("start") final int skipBlocks,
			@PathParam("size") final int blockSize)
			throws IllegalArgumentException, NdexException {

    	if(query.getAccountName() != null)
    		query.setAccountName(query.getAccountName().toLowerCase());
        
    	try (NetworkDAO dao = new NetworkDAO()) {

			NetworkSearchResult result = dao.findNetworks(query, skipBlocks, blockSize, this.getLoggedInUser());
			return result;

        } catch (Exception e) {
        	throw new NdexException(e.getMessage());
        }
	}

	@POST
	@PermitAll
	@Path("/search/{start}/{size}")
	@Produces("application/json")
	@ApiDoc("This method returns a list of NetworkSummary objects based on a POSTed query JSON object. " +
            "The maximum number of NetworkSummary objects to retrieve in the query is set by the integer " +
            "value 'blockSize' while 'skipBlocks' specifies number of blocks that have already been read. " +
            "For more information, please click <a href=\"http://www.ndexbio.org/using-the-ndex-server-api/#searchNetwork\">here</a>.")
	public Collection<NetworkSummary> searchNetworkV1(
			final SimpleNetworkQuery query,
			@PathParam("start") final int skipBlocks,
			@PathParam("size") final int blockSize)
			throws IllegalArgumentException, NdexException {
		
		return searchNetwork_solr(query, skipBlocks, blockSize).getNetworks();
	}
	
	
    @PUT
    @Path("/asCX/{networkid}")
    @Consumes("multipart/form-data")
    @Produces("text/plain")
 
    public String updateCXNetwork(final @PathParam("networkid") String networkIdStr,
    		MultipartFormDataInput input) throws Exception 
    {
    	
        UUID networkId = UUID.fromString(networkIdStr);

     //   String ownerAccName = null;

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
			
		//	ownerAccName = daoNew.getNetworkOwnerAcc(networkId);
			
	        UUID tmpNetworkId = storeRawNetwork (input);
	        String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId.toString() + "/network.cx";
			long fileSize = new File(cxFileName).length();
				
	        daoNew.clearNetworkSummary(networkId, fileSize);

			java.nio.file.Path src = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + tmpNetworkId);
			java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId);
			FileUtils.deleteDirectory(new File(Configuration.getInstance().getNdexRoot() + "/data/" + networkId));
			Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
		
/*			String urlStr = Configuration.getInstance().getHostURI()  + 
			            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ networkIdStr;
			ProvenanceEntity entity = new ProvenanceEntity();
			entity.setUri(urlStr + "/summary");
			ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.CX_NETWORK_UPDATE, new Timestamp(System.currentTimeMillis()));

			List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
			eventProperties.add( new SimplePropertyValuePair("user name", this.getLoggedInUser().getUserName()) ) ;

			event.setProperties(eventProperties);		
			ProvenanceEntity inputEntity =daoNew.getProvenance(networkId);
			event.addInput(inputEntity);
			entity.setCreationEvent(event);

			daoNew.setProvenance(networkId, entity); */
			
			daoNew.commit();
		//	daoNew.unlockNetwork(networkId);
			
           } catch (SQLException | NdexException | IOException e) {
        	   e.printStackTrace();
        	   daoNew.rollback();
        	   daoNew.unlockNetwork(networkId);  

        	   throw e;
           } 
			
        }  
    	      
	     NdexServerQueue.INSTANCE.addSystemTask(new CXNetworkLoadingTask(networkId, /*ownerAccName,*/ true,null,null));
	     return networkIdStr; 
    }

    
    
	@DELETE
	@Path("/{networkid}")
	@Produces("application/json")
    @ApiDoc("Deletes the network specified by networkId. There is no method to undo a deletion, so care " +
	        "should be exercised. A user can only delete networks that they own.")
	public void deleteNetwork(final @PathParam("networkid") String id) throws NdexException, SQLException {
		    
		try (NetworkDAO networkDao = new NetworkDAO()) {
			UUID networkId = UUID.fromString(id);
			UUID userId = getLoggedInUser().getExternalId();
			if(networkDao.isAdmin(networkId, userId) ) {
				if (!networkDao.isReadOnly(networkId) ) {
					if ( !networkDao.networkIsLocked(networkId)) {

						networkDao.deleteNetwork(UUID.fromString(id), getLoggedInUser().getExternalId());
						networkDao.commit();
						
						String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkId.toString();
						try {
							
								FileUtils.deleteDirectory(new File(pathPrefix));
						} catch (IOException e) {
								e.printStackTrace();
								throw new NdexException("Failed to delete directory. Error: " + e.getMessage());
						}
							
						NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskDeleteNetwork(networkId));
				
						return;
					}
					throw new NdexException ("Network is locked by another updating process. Please try again.");
				}
				  throw new NdexException("Can't delete a read-only network.");
			}	
			throw new NdexException("Only network owner can delete a network.");	
		} catch ( IOException e ) {
			throw new NdexException ("Error occurred when deleting network: " + e.getMessage(), e);
		}
			
	}
	

	/**************************************************************************
	 * Saves an uploaded network file. Determines the type of file uploaded,
	 * saves the file, and creates a task.
	 *
	 * @param uploadedNetwork
	 *            The uploaded network file.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to parse the file, or create the network in the
	 *             database.
	 * @throws IOException 
	 **************************************************************************/
	/*
	 * refactored to support non-transactional database operations
	 */
	@POST
	@Path("/upload")
	@Consumes("multipart/form-data")
	@Produces("application/json")
    @ApiDoc("Upload a network file into the current users NDEx account. This can take some time while background " +
            "processing converts the data from the file into the common NDEx format. This method errors if the " +
            "network is missing or if it has no filename or no file data.")
	public Task uploadNetwork( MultipartFormDataInput input) 
                  //@MultipartForm UploadedFile uploadedNetwork)
			throws IllegalArgumentException, SecurityException, NdexException, IOException {

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
			throw iae;
		} catch (Exception e) {
			throw new NdexException(e.getMessage());
		}

		return processNetworkTask;
	}



	
	
	@POST
	@Path("/export")
	@Produces("application/json")

	public Map<UUID,UUID> exportNetworks(NetworkExportRequest exportRequest)

			throws IllegalArgumentException, NdexException, SQLException, IOException {
		
		    if ( !exportRequest.getNetworkFormat().toLowerCase().equals("cx"))
		    	throw new NdexException("Networks can only be exported in cx fromat in this server.");
		    
		    Map<UUID,UUID> result = new TreeMap<>();
			try (NetworkDAO networkDao = new NetworkDAO()) {
				try (TaskDAO taskdao = new TaskDAO()) {

					for ( UUID networkID : exportRequest.getNetworkIds()) {
						Task t = new Task();
						Timestamp currentTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
						t.setCreationTime(currentTime);
						t.setModificationTime(currentTime);
						t.setStartTime(currentTime);
						t.setFinishTime(currentTime);
						t.setDescription("network export");
						t.setTaskType(TaskType.EXPORT_NETWORK_TO_FILE);
						t.setFormat(FileFormat.CX);
						t.setTaskOwnerId(getLoggedInUserId());
						t.setResource(networkID.toString());
						if (! networkDao.isReadable(networkID, getLoggedInUserId())) {
							t.setStatus(Status.FAILED);
							t.setMessage("Network " + networkID + " is not found for user.");
							//throw new NdexException ("Network " + networkID + " is not found.");
						}	else {
							t.setStatus(Status.QUEUED);
							
							NetworkSummary s = networkDao.getNetworkSummaryById(networkID);
							t.setAttribute("downloadFileName", s.getName());
							t.setAttribute("downloadFileExtension", "CX");
						    

						}
						UUID taskId = taskdao.createTask(t);
						taskdao.commit();
						
						result.put(networkID, taskId);
						
						if ( t.getStatus() == Status.QUEUED)
						NdexServerQueue.INSTANCE.addUserTask(new NetworkExportTask(t));

					}
				}
			}
			
			return result;
		    
	}
	
	
	   @POST
	//	@PermitAll

	   @Path("/asCX")
//	   @Produces("application/json")
	   @Produces("text/plain")
	   @Consumes("multipart/form-data")
	   @ApiDoc("Create a network from the uploaded CX stream. The input cx data is expected to be in the CXNetworkStream field of posted multipart/form-data. "
	   		+ "There is an optional 'provenance' field in the form. Users can use this field to pass in a JSON string of ProvenanceEntity object. When a user pass"
	   		+ " in this object, NDEx server will add this object to the provenance history of the CX network. Otherwise NDEx server will create a ProvenanceEntity "
	   		+ "object and add it to the provenance history of the CX network.")
	   public String createCXNetwork( MultipartFormDataInput input) throws Exception
	   {
		   
		   try (UserDAO dao = new UserDAO()) {
			   dao.checkDiskSpace(getLoggedInUserId());
		   }
	   
		   UUID uuid = storeRawNetwork ( input);
		   String uuidStr = uuid.toString();
		   
			accLogger.info("[data]\t[uuid:" +uuidStr + "]" );

		   
/*		   String urlStr = Configuration.getInstance().getHostURI()  + 
		            Configuration.getInstance().getRestAPIPrefix()+"/network/"+ uuidStr;

		   ProvenanceEntity entity = new ProvenanceEntity();
		   entity.setUri(urlStr + "/summary"); */
		   
		   String cxFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/network.cx";
		   long fileSize = new File(cxFileName).length();

		   // create entry in db. 
	       try (NetworkDAO dao = new NetworkDAO()) {
	    	//   NetworkSummary summary = 
	    			 dao.CreateEmptyNetworkEntry(uuid, getLoggedInUser().getExternalId(), getLoggedInUser().getUserName(), fileSize,null);
	    	   
       
		/*	   ProvenanceEvent event = new ProvenanceEvent(NdexProvenanceEventType.CX_CREATE_NETWORK, summary.getModificationTime());

				List<SimplePropertyValuePair> eventProperties = new ArrayList<>();
				eventProperties.add( new SimplePropertyValuePair("user name", this.getLoggedInUser().getUserName()) ) ;
				event.setProperties(eventProperties);

				entity.setCreationEvent(event);
				dao.setProvenance(summary.getExternalId(), entity); */
	    	   dao.commit();
	       } catch (Throwable e) {
	    	   e.printStackTrace();
	    	   throw e;
	       }
	       
	       NdexServerQueue.INSTANCE.addSystemTask(new CXNetworkLoadingTask(uuid, /*getLoggedInUser().getUserName(),*/ false, null,null));
	       
		   return  uuidStr ;

	   	}
	   

	   private static UUID storeRawNetwork (MultipartFormDataInput input) throws IOException {
		   Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
	       
			 //      ProvenanceEntity entity = this.getProvenanceEntityFromMultiPart(uploadForm);
			       
			 //Get file data to save
			 List<InputPart> inputParts = uploadForm.get("CXNetworkStream");
					
				   byte[] bytes = new byte[8192];
				   UUID uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
				   String uuidStr = uuid.toString();
				   String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr;
				   
				   //Create dir
				   java.nio.file.Path dir = Paths.get(pathPrefix);
				   Files.createDirectory(dir);
				   
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
	   
	   

	   
}
