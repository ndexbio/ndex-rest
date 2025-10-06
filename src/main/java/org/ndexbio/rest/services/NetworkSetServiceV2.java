package org.ndexbio.rest.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import org.ndexbio.common.models.dao.postgresql.NetworkSetDAO;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.FolderRequest;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import io.swagger.v3.oas.annotations.Operation;


@Path("/v2/networkset")
@Deprecated
public class NetworkSetServiceV2 extends NdexService {

	
	public NetworkSetServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	
	@POST
	@Deprecated
	@Operation(summary = "Create a Network Set (DEPRECATED)", description = "Create a network set. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("text/plain")
	public Response createNetworkSet(final NetworkSet newNetworkSet)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException, URISyntaxException, Exception {
	
		if ( newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0) 
			throw new NdexException ("Network set name is required.");
		
		// Create folder with values that are passed in NetworkSet object
		UUID folderId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
		
		try (FolderDAO dao = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			dao.createFolder(folderId, getLoggedInUserId(), null, newNetworkSet.getName(), newNetworkSet.getDescription());
			dao.commit();
		}
		
		URI l = new URI (Configuration.getInstance().getHostURI()  + 
	            Configuration.getInstance().getRestAPIPrefix()+"/networkset/"+ folderId.toString());
		
		return Response.created(l).entity(l).build();
	}
	
	
	@PUT
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Update a Network Set (DEPRECATED)", description = "Updates a project based on the serialized project object in the PUT data. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	public void updateNetworkSet(final NetworkSet newNetworkSet,
			@PathParam("networksetid") final String id)
			throws  DuplicateObjectException,
			NdexException,  SQLException, JsonProcessingException, Exception {
	
		if ( newNetworkSet.getName() == null || newNetworkSet.getName().length() == 0) 
			throw new NdexException ("Network set name is required.");
		
		UUID setId = UUID.fromString(id);
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			if (networkSetDAO.isNetworkSetOwner(setId, getLoggedInUserId())) {
				// Update existing networkset
				networkSetDAO.updateNetworkSet(setId, newNetworkSet.getName(), newNetworkSet.getDescription(), getLoggedInUserId(), newNetworkSet.getProperties());
				networkSetDAO.commit();
				return;
			}
		} catch (SQLException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (folderDAO.isFolderOwner(setId, getLoggedInUserId())) {
				// Update existing folder
				FolderRequest folderRequest = new FolderRequest();
				folderRequest.setName(newNetworkSet.getName());
				folderDAO.updateFolder(setId, newNetworkSet.getName(), null, getLoggedInUserId(), newNetworkSet.getDescription());
				folderDAO.commit();
				return;
			}
		} catch (SQLException e) {
			// Folder not found
		}
		
		// If neither networkset nor folder exists, create a new folder
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			folderDAO.createFolder(setId, getLoggedInUserId(), null, newNetworkSet.getName(), newNetworkSet.getDescription());
			folderDAO.commit();
		}
	}
	
	
	@DELETE
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Delete a Network Set (DEPRECATED)", description = "Delete a network set. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("application/json")
	public void deleteNetworkSet(@PathParam("networksetid") final String networkSetIdStr)
			throws NdexException, SQLException, Exception {
		
		UUID setId = UUID.fromString(networkSetIdStr);
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			if (networkSetDAO.isNetworkSetOwner(setId, getLoggedInUserId())) {
				// Delete existing networkset
				networkSetDAO.deleteNetworkSet(setId);
				networkSetDAO.commit();
				return;
			}
		} catch (SQLException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (folderDAO.isFolderOwner(setId, getLoggedInUserId())) {
				// Delete existing folder
				folderDAO.deleteFolder(setId, true, false);
				folderDAO.commit();
				return;
			}
		} catch (SQLException e) {
			// Folder not found
		}
		
		throw new ObjectNotFoundException("Network set or folder " + setId + " not found.");
	}
	
	
	@GET
	@PermitAll
	@Path("/{networksetid}")
	@Deprecated
	@Operation(summary = "Get a Network Set (DEPRECATED)", description = "Gets a Network Set. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("application/json")
	public NetworkSet getNetworkSet(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("accesskey") String accessKey)
			throws ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException, Exception {
		
		UUID setId = UUID.fromString(networkSetIdStr);
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			NetworkSet networkSet = networkSetDAO.getNetworkSet(setId, getLoggedInUserId(), accessKey);
			return networkSet;
		} catch (SQLException | ObjectNotFoundException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (folderDAO.isReadable(setId, getLoggedInUserId()) || folderDAO.accessKeyIsValid(setId, accessKey)) {
				NdexFolder folder = folderDAO.getFolder(setId, getLoggedInUserId(), accessKey);
				
				// Convert folder to NetworkSet format
				NetworkSet networkSet = new NetworkSet();
				networkSet.setExternalId(folder.getExternalId());
				networkSet.setName(folder.getName());
				networkSet.setCreationTime(folder.getCreationTime());
				networkSet.setModificationTime(folder.getModificationTime());
				networkSet.setOwnerId(getLoggedInUserId());
				
				return networkSet;
			}
		} catch (SQLException | ObjectNotFoundException e) {
			// Folder not found
		}
		
		throw new ObjectNotFoundException("Network set or folder " + setId + " not found.");
	}

	@POST
	@Path("/{networksetid}/members")
	@Deprecated
	@Operation(summary = "Add networks to Network Set (DEPRECATED)", description = "Add a list of networks to this set. The posted data is a list of network ids. All the networks should be visible to the owner of network set. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("text/plain")
	public Response addNetworksToSet(final List<UUID> networkIds,
				@PathParam("networksetid") final String networkSetIdStr )
			throws  DuplicateObjectException,
			NdexException,  SQLException, URISyntaxException, Exception {
	
		UUID setId = UUID.fromString(networkSetIdStr);
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			if (networkSetDAO.isNetworkSetOwner(setId, getLoggedInUserId())) {
				// Add networks to existing networkset
				networkSetDAO.addNetworksToNetworkSet(setId, networkIds);
				networkSetDAO.commit();
				
				URI l = new URI (Configuration.getInstance().getHostURI()  + 
				            Configuration.getInstance().getRestAPIPrefix()+"/networkset/"+ setId.toString()+"/members");
				return Response.created(l).entity(l).build();
			}
		} catch (SQLException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO();
		    ShortcutDAO shortcutDAO = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			if (folderDAO.isFolderOwner(setId, getLoggedInUserId())) {
				// Add networks as shortcuts to existing folder
				for (UUID networkId : networkIds) {
					UUID shortcutId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
					// TODO: get network name and set it as the name of the shortcut with "Shortcut to " prefix
					shortcutDAO.createShortcut(shortcutId, getLoggedInUserId(), setId, networkId.toString(), networkId, FileType.NETWORK);
				}
				shortcutDAO.commit();
				URI l = new URI (Configuration.getInstance().getHostURI()  + 
				            Configuration.getInstance().getRestAPIPrefix()+"/networkset/"+ setId.toString()+"/members");
				return Response.created(l).entity(l).build();
			}
		} catch (SQLException e) {
			// Folder not found
		}
		
		throw new ObjectNotFoundException("Network set or folder " + setId + " not found.");
	}
	
	
	@DELETE
	@Path("/{networksetid}/members")
	@Deprecated
	@Operation(summary = "Delete networks from Network Set (DEPRECATED)", description = "Delete networks from a networks set. Posted data is a list of network ids. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("application/json")
	public void deleteNetworkSet(final List<UUID> networkIds,
			@PathParam("networksetid") final String networkSetIdStr )
			throws NdexException, SQLException, Exception {
		
		UUID setId = UUID.fromString(networkSetIdStr);
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			if (networkSetDAO.isNetworkSetOwner(setId, getLoggedInUserId())) {
				// Delete networks from existing networkset
				networkSetDAO.deleteNetworksToNetworkSet(setId, networkIds);
				networkSetDAO.commit();
				return;
			}
		} catch (SQLException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO();
			ShortcutDAO shortcutDAO = Configuration.getInstance().getDAOFactory().getShortcutDAO()) {
			if (folderDAO.isFolderOwner(setId, getLoggedInUserId())) {
				for (UUID networkId : networkIds) {
					// Find shortcut in this folder pointing to this network
					List<NdexShortcut> shortcuts = shortcutDAO.listShortcutsOfUser(getLoggedInUserId(), 1000);
					for (NdexShortcut shortcut : shortcuts) {
						if (setId.equals(shortcut.getParent()) && networkId.equals(shortcut.getTarget()) && shortcut.getTargetType() == FileType.NETWORK) {
							shortcutDAO.deleteShortcut(shortcut.getExternalId(), true);
						}
					}
				}
				shortcutDAO.commit();
				return;
			}
		} catch (SQLException e) {
			// Folder not found
		}
		
		throw new ObjectNotFoundException("Network set or folder " + setId + " not found.");
	}
	
	
	@GET
	@Path("/{networksetid}/accesskey")
	@Deprecated
	@Operation(summary = "Get Access key of Network Set (DEPRECATED)", description = "This function returns an access key to the user. This access key will allow any user to have read access to member networks of this network set regardless if that user has READ privilege on that network. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("application/json")
	public Map<String,String> getNetworkSetAccessKey(@PathParam("networksetid") final String networkSetIdStr)
			throws IllegalArgumentException, NdexException, SQLException, Exception {
  	
		UUID networkSetId = UUID.fromString(networkSetIdStr);
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			if (networkSetDAO.isNetworkSetOwner(networkSetId, getLoggedInUserId())) {
				String key = networkSetDAO.getNetworkSetAccessKey(networkSetId);
				if (key == null || key.length() == 0)
					return null;
				Map<String,String> result = new HashMap<>(1);
				result.put("accessKey", key);
				return result;
			}
		} catch (SQLException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (folderDAO.isFolderOwner(networkSetId, getLoggedInUserId())) {
				String key = folderDAO.enableFolderAccessKey(networkSetId);
				if (key == null || key.length() == 0)
					return null;
				Map<String,String> result = new HashMap<>(1);
				result.put("accessKey", key);
				return result;
			}
		} catch (SQLException e) {
			// Folder not found
		}
		
		throw new UnauthorizedOperationException("User is not the owner of this network set or folder.");
	}  
		
	@PUT
	@Path("/{networksetid}/accesskey")
	@Deprecated
	@Operation(summary = "Disable/Enable Access Key on Network Set (DEPRECATED)", description = "This function turns on/off the access key. It returns the key when it is enabled, and returns http code 204 when it is disabled. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("application/json")	
	public  Map<String,String> disableNetworkAccessKey(@PathParam("networksetid") final String networkSetIdStr,
			@QueryParam("action") String action)
			throws IllegalArgumentException, NdexException, SQLException, Exception {
  	
		UUID networkSetId = UUID.fromString(networkSetIdStr);
		if ( ! action.equalsIgnoreCase("disable") && ! action.equalsIgnoreCase("enable"))
			throw new NdexException("Value of 'action' parameter can only be 'disable' or 'enable'");
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			if (networkSetDAO.isNetworkSetOwner(networkSetId, getLoggedInUserId())) {
				String key = null;
				if ( action.equalsIgnoreCase("disable"))
					networkSetDAO.disableNetworkSetAccessKey(networkSetId);
				else 
					key = networkSetDAO.enableNetworkSetAccessKey(networkSetId);
				networkSetDAO.commit();
				
				if (key == null || key.length() == 0)
					return null;
				Map<String,String> result = new HashMap<>(1);
				result.put("accessKey", key);
				return result;
			}
		} catch (SQLException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (folderDAO.isFolderOwner(networkSetId, getLoggedInUserId())) {
				String key = null;
				if (action.equalsIgnoreCase("disable")) {
					folderDAO.disableFolderAccessKey(networkSetId);
				} else {
					key = folderDAO.enableFolderAccessKey(networkSetId);
				}
				folderDAO.commit();
				
				if (key == null || key.length() == 0)
					return null;
				Map<String,String> result = new HashMap<>(1);
				result.put("accessKey", key);
				return result;
			}
		} catch (SQLException e) {
			// Folder not found
		}
		
		throw new UnauthorizedOperationException("User is not the owner of this network set or folder.");
	}  
	
	
	@PUT
	@Path("/{networksetid}/systemproperty")
	@Deprecated
	@Operation(summary = "Update Network Set System Properties (DEPRECATED)", description = "Network Set System properties are the properties that describe the network set's status in a particular NDEx server. DEPRECATED: Network sets are being converted to folders. This endpoint will continue to work but is deprecated.")
	@Produces("application/json")
  
	public void setNetworkFlag(
			@PathParam("networksetid") final String networkSetIdStr,
			final Map<String,Object> parameters)

			throws IllegalArgumentException, NdexException, SQLException, Exception {
		
		UUID networkSetId = UUID.fromString(networkSetIdStr);
		
		// Check if id exists in networkset table first
		try (NetworkSetDAO networkSetDAO = new NetworkSetDAO()) {
			if (networkSetDAO.isNetworkSetOwner(networkSetId, getLoggedInUserId())) {
				if ( parameters.containsKey("showcase")) {
					boolean bv = ((Boolean)parameters.get("showcase")).booleanValue();
					networkSetDAO.setShowcaseFlag(networkSetId, bv);
				}
				networkSetDAO.commit();
				return;
			}
		} catch (SQLException e) {
			// NetworkSet not found, continue to check folder
		}
		
		// Check if id exists in folder table
		try (FolderDAO folderDAO = Configuration.getInstance().getDAOFactory().getFolderDAO()) {
			if (folderDAO.isFolderOwner(networkSetId, getLoggedInUserId())) {
				// Update showcase for folder - do nothing for folder as requested
				return;
			}
		} catch (SQLException e) {
			// Folder not found
		}
		
		throw new UnauthorizedOperationException("User is not the owner of this network set or folder.");
	}

	
	
	
}
