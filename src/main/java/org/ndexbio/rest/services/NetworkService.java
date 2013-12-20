package org.ndexbio.rest.services;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.ITask;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.domain.Priority;
import org.ndexbio.rest.domain.Status;
import org.ndexbio.rest.domain.TaskType;
import org.ndexbio.rest.exceptions.DuplicateObjectException;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.gremlin.NetworkQueries;
import org.ndexbio.rest.gremlin.SearchSpec;
import org.ndexbio.rest.helpers.Configuration;
import org.ndexbio.rest.helpers.IdConverter;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Namespace;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.NetworkQueryParameters;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.Term;
import org.ndexbio.rest.models.BaseTerm;
import org.ndexbio.rest.models.FunctionTerm;
import org.ndexbio.rest.models.Node;
import org.ndexbio.rest.models.Edge;
import org.ndexbio.rest.models.Citation;
import org.ndexbio.rest.models.Support;
import org.ndexbio.rest.models.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import com.tinkerpop.frames.VertexFrame;

@Path("/networks")
public class NetworkService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(NetworkService.class);
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public NetworkService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Suggests terms that start with the partial term.
    * 
    * @param networkId
    *            The network ID.
    * @param partialTerm
    *            The partially entered term.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to query the database.
    * @return A collection of terms that start with the partial term.
    **************************************************************************/
    @GET
    @Path("/{networkId}/autosuggest/{partialTerm}")
    @Produces("application/json")
    public Collection<String> autoSuggestTerms(@PathParam("networkId")final String networkId, @PathParam("partialTerm")final String partialTerm) throws IllegalArgumentException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("No network ID was specified.");
        else if (partialTerm == null || partialTerm.isEmpty() || partialTerm.length() < 3)
            return null;
        
        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(IdConverter.toRid(networkId), INetwork.class);
            if (network == null)
                return null;
            else
            {
                final Collection<String> foundTerms = new ArrayList<String>();
                for (final ITerm networkTerm : network.getTerms())
                {
                    if (networkTerm instanceof IBaseTerm)
                    {
                        if (((IBaseTerm)networkTerm).getName().toLowerCase().startsWith(partialTerm.toLowerCase()))
                        {
                            foundTerms.add(((IBaseTerm)networkTerm).getName());
                            
                            if (foundTerms.size() == 20)
                                return foundTerms;
                        }
                    }
                }

                return foundTerms;
            }
        }
        catch (Exception e)
        {
            _logger.error("Failed to retrieve auto-suggest data for: " + partialTerm + ".", e);
            throw new NdexException("Failed to retrieve auto-suggest data for: " + partialTerm + ".");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Creates a network.
    * 
    * @param ownerId
    *            The ID of the user creating the group.
    * @param newNetwork
    *            The network to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws DuplicateObjectException
    *            The user already has a network with the same title.
    * @throws NdexException
    *            Failed to create the network in the database.
    * @return The newly created network.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Network createNetwork(final Network newNetwork) throws IllegalArgumentException, DuplicateObjectException, NdexException
    {
        if (newNetwork == null)
            throw new IllegalArgumentException("The network to create is null.");
        else if (newNetwork.getTitle() == null || newNetwork.getTitle().isEmpty())
            throw new IllegalArgumentException("The network does not have a title.");

        try
        {
            setupDatabase();

            final IUser networkOwner = _orientDbGraph.getVertex(IdConverter.toRid(this.getLoggedInUser().getId()), IUser.class);
            
            final List<ODocument> existingNetworks = _ndexDatabase.query(new OSQLSynchQuery<Object>("select @RID from Network where out_networkMemberships.out_membershipMember.username = '"
                + networkOwner.getUsername() + "' and title = '" + newNetwork.getTitle() + "'"));
            if (!existingNetworks.isEmpty())
                throw new DuplicateObjectException("You already have a network titled: " + newNetwork.getTitle());


            final Map<String, VertexFrame> networkIndex = new HashMap<String, VertexFrame>();

            final INetwork network = _orientDbGraph.addVertex("class:network", INetwork.class);
            network.setIsPublic(newNetwork.getIsPublic());
            network.setFormat(newNetwork.getFormat());
            network.setSource(newNetwork.getSource());
            network.setTitle(newNetwork.getTitle());
            
            if (newNetwork.getMembers() == null || newNetwork.getMembers().size() == 0)
            {
                final INetworkMembership membership = _orientDbGraph.addVertex("class:networkMembership", INetworkMembership.class);
                membership.setPermissions(Permissions.ADMIN);
                membership.setMember(networkOwner);
                membership.setNetwork(network);
                
                networkOwner.addNetwork(membership);
                network.addMember(membership);
            }
            else
            {
                for (Membership member : newNetwork.getMembers())
                {
                    final IUser networkMember = _orientDbGraph.getVertex(IdConverter.toRid(member.getResourceId()), IUser.class);
                    
                    final INetworkMembership membership = _orientDbGraph.addVertex("class:networkMembership", INetworkMembership.class);
                    membership.setPermissions(member.getPermissions());
                    membership.setMember(networkMember);
                    membership.setNetwork(network);
        
                    networkMember.addNetwork(membership);
                    network.addMember(membership);
                }
            }

            // First create all namespaces used by the network
            createNamespaces(network, newNetwork, networkIndex);

            // Then create terms which may reference the namespaces
            // The terms are created in order of reference - later terms may
            // refer to earlier terms
            createTerms(network, newNetwork, networkIndex);

            // Then create nodes that may reference terms
            createNodes(network, newNetwork, networkIndex);

            // Then create edges that reference terms nodes
            createEdges(network, newNetwork, networkIndex);

            // Then create citations that reference edges
            createCitations(network, newNetwork, networkIndex);

            // Then create supports that reference edges and citations
            createSupports(network, newNetwork, networkIndex);

            _orientDbGraph.getBaseGraph().commit();

            return new Network(network);
        }
        catch (DuplicateObjectException doe)
        {
            throw doe;
        }
        catch (Exception e)
        {
            _logger.error("Failed to create network: " + newNetwork.getTitle() + ".", e);
            _orientDbGraph.getBaseGraph().rollback();
            throw new NdexException("Failed to create the network.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Deletes a network.
    * 
    * @param networkId
    *            The ID of the network to delete.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The network doesn't exist.
    * @throws NdexException
    *            Failed to delete the network from the database.
    **************************************************************************/
    @DELETE
    @Path("/{networkId}")
    @Produces("application/json")
    public void deleteNetwork(@PathParam("networkId")final String networkId) throws IllegalArgumentException, ObjectNotFoundException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("No network ID was specified.");

        final ORID networkRid = IdConverter.toRid(networkId);
        
        try
        {
            setupDatabase();

            final INetwork networkToDelete = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (networkToDelete == null)
                throw new ObjectNotFoundException("Network", networkId);
            else if (!hasPermission(new Network(networkToDelete), Permissions.ADMIN))
                throw new SecurityException("Insufficient privileges to delete the group.");

            final List<ODocument> adminCount = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Membership where in_members = ? and permissions = 'ADMIN'"));
            if (adminCount == null || adminCount.isEmpty())
                throw new NdexException("Unable to count ADMIN members.");
            else if ((long)adminCount.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a network that contains other ADMIN members.");

            for (INetworkMembership networkMembership : networkToDelete.getMembers())
                _orientDbGraph.removeVertex(networkMembership.asVertex());

            final List<ODocument> networkChildren = _ndexDatabase.query(new OSQLSynchQuery<Object>("select @rid from (traverse * from " + networkRid + " while @class <> 'network' and @class <> 'user' and @class <> 'group')"));
            for (ODocument networkChild : networkChildren)
            {
                final OrientElement element = _orientDbGraph.getBaseGraph().getElement(networkChild.field("rid", OType.LINK));
                if (element != null)
                    element.remove();
            }
            
            _orientDbGraph.removeVertex(networkToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (SecurityException | NdexException ne)
        {
            throw ne;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Network", networkId);
            
            _orientDbGraph.getBaseGraph().rollback();
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Searches for a network.
    * 
    * @param searchParameters
    *            The search parameters.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to query the database.
    * @return Networks that match the search parameters.
    **************************************************************************/
    @POST
    @PermitAll
    @Path("/search")
    @Produces("application/json")
    public List<Network> findNetworks(SearchParameters searchParameters) throws IllegalArgumentException, NdexException
    {
        if (searchParameters == null)
            throw new IllegalArgumentException("Search Parameters are empty.");
        else if (searchParameters.getSearchString() == null || searchParameters.getSearchString().isEmpty())
            throw new IllegalArgumentException("No search string was specified.");
        else
            searchParameters.setSearchString(searchParameters.getSearchString().toUpperCase().trim());

        final List<Network> foundNetworks = new ArrayList<Network>();

        final int startIndex = searchParameters.getSkip() * searchParameters.getTop();
        String query = "select from Network where isPublic = true";
        
        if (this.getLoggedInUser() != null)
            query += " or out_networkMemberships.out_membershipMember.username = '" + this.getLoggedInUser().getUsername() + "'";
        
        query += " and (title.toUpperCase() like '%" + searchParameters.getSearchString() + "%'"
            + " or description.toUpperCase() like '%" + searchParameters.getSearchString() + "%')"
            + " order by creation_date desc skip " + startIndex + " limit " + searchParameters.getTop();

        try
        {
            setupDatabase();

            final List<ODocument> networkDocumentList = _orientDbGraph
                .getBaseGraph()
                .getRawGraph()
                .query(new OSQLSynchQuery<ODocument>(query));

            for (final ODocument document : networkDocumentList)
                foundNetworks.add(new Network(_orientDbGraph.getVertex(document, INetwork.class)));

            return foundNetworks;
        }
        catch (Exception e)
        {
            _logger.error("Failed to search networks.", e);
            throw new NdexException("Failed to search networks.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Gets a network by ID.
    * 
    * @param networkId
    *            The ID of the network.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws SecurityException
    *            The user doesn't have access to the network.
    * @throws NdexException
    *            Failed to query the database.
    * @return The network.
    **************************************************************************/
    @GET
    @Path("/{networkId}")
    @Produces("application/json")
    public Network getNetwork(@PathParam("networkId")final String networkId) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("No network ID was specified.");

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(IdConverter.toRid(networkId), INetwork.class);
            if (network == null)
                return null;
            else if (!network.getIsPublic())
            {
                for (Membership userMembership : this.getLoggedInUser().getNetworks())
                {
                    if (userMembership.getResourceId().equals(networkId))
                        return new Network(network);
                }
                
                throw new SecurityException("You do not have access to that network.");
            }
            else
                return new Network(network);
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Gets a page of Edges for the specified network, along with the
    * supporting nodes, terms, namespaces, supports, and citations.
    * 
    * @param networkId
    *            The network ID.
    * @param skip
    *            The number of edges to skip.
    * @param top
    *            The number of edges to retrieve.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to query the database.
    * @return The edges of the network.
    **************************************************************************/
    @GET
    @Path("/{networkId}/edges/{skip}/{top}")
    @Produces("application/json")
    public Network getEdges(@PathParam("networkId")final String networkId, @PathParam("skip")final int skip, @PathParam("top")final int top) throws IllegalArgumentException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("No network ID was specified.");
        else if (top < 1)
            throw new IllegalArgumentException("Number of results to return is less than 1.");

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(IdConverter.toRid(networkId), INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", networkId);

            int counter = 0;
            final List<IEdge> foundIEdges = new ArrayList<IEdge>();
            final int startIndex = skip * top;

            for (final IEdge networkEdge : network.getNdexEdges())
            {
                if (counter >= startIndex)
                    foundIEdges.add(networkEdge);

                counter++;
                if (counter >= startIndex + top)
                    break;
            }

            return getNetworkBasedOnFoundEdges(foundIEdges, network);
        }
        catch (ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            _logger.error("Failed to query network: " + networkId + ".", e);
            throw new NdexException(e.getMessage());
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Get a subnetwork network based on network query parameters.
    * 
    * @param networkId
    *            The network ID.
    * @param queryParameters
    *            The query parameters.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to query the database.
    * @return A subnetwork of the network.
    **************************************************************************/
    @POST
    @Path("/{networkId}/query")
    @Produces("application/json")
    public Network queryNetwork(@PathParam("networkId")final String networkId, final NetworkQueryParameters queryParameters) throws IllegalArgumentException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("No network ID was specified.");
        
        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(IdConverter.toRid(networkId), INetwork.class);
            if (network == null)
                return null;
            else
            {
                List<Term> baseTerms = getBaseTermsByName(network, queryParameters.getStartingTermStrings().get(0));
                if (!baseTerms.isEmpty())
                {
                    queryParameters.addStartingTermId(baseTerms.get(0).getId());
                    
                    List<IEdge> foundIEdges = neighborhoodQuery(network, queryParameters);
                    return getNetworkBasedOnFoundEdges(foundIEdges, network);
                }
                else
                    return null;
            }
        }
        catch (Exception e)
        {
            _logger.error("Failed to query network: " + networkId + ".", e);
            throw new NdexException("Failed to query the network.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Removes a member from a network.
    * 
    * @param networkId
    *            The network ID.
    * @param userId
    *            The ID of the member to remove.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The network or member doesn't exist.
    * @throws SecurityException
    *            The user doesn't have access to change members.
    * @throws NdexException
    *            Failed to query the database.
    **************************************************************************/
    @DELETE
    @Path("/{networkId}/member/{userId}")
    @Produces("application/json")
    public void removeMember(@PathParam("networkId")final String networkId, @PathParam("userId")final String userId) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("No network ID was specified.");
        else if (userId == null || userId.isEmpty())
            throw new IllegalArgumentException("No member was specified.");
        
        try
        {
            setupDatabase();
            
            final ORID networkRid = IdConverter.toRid(networkId);
            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);

            if (network == null)
                throw new ObjectNotFoundException("Network", networkId);
            else if (!hasPermission(new Network(network), Permissions.ADMIN))
                throw new SecurityException("Access denied.");
    
            final IUser user = _orientDbGraph.getVertex(IdConverter.toRid(userId), IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", userId);
            
            for (INetworkMembership networkMember : network.getMembers())
            {
                String memberId = IdConverter.toJid((ORID)networkMember.getMember().asVertex().getId());
                if (memberId.equals(userId))
                {
                    if (countAdminMembers(networkRid) < 2)
                        throw new SecurityException("Cannot remove the only ADMIN member.");
                    
                    network.removeMember(networkMember);
                    user.removeNetwork(networkMember);
                    _orientDbGraph.getBaseGraph().commit();
                    return;
                }
            }
        }
        catch (ObjectNotFoundException | SecurityException ne)
        {
            throw ne;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Network", networkId);
            
            _logger.error("Failed to remove member.", e);
            _orientDbGraph.getBaseGraph().rollback();
            throw new NdexException("Failed to remove member.");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Changes a member's permissions to a network.
    * 
    * @param networkId
    *            The network ID.
    * @param networkMember
    *            The member being updated.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The network or member doesn't exist.
    * @throws SecurityException
    *            The user doesn't have access to change members.
    * @throws NdexException
    *            Failed to query the database.
    **************************************************************************/
    @POST
    @Path("/{networkId}/member")
    @Produces("application/json")
    public void updateMember(@PathParam("networkId")final String networkId, final Membership networkMember) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
        if (networkId == null || networkId.isEmpty())
            throw new IllegalArgumentException("No network ID was specified.");
        else if (networkMember == null)
            throw new IllegalArgumentException("The member to update is empty.");
        else if (networkMember.getResourceId() == null || networkMember.getResourceId().isEmpty())
            throw new IllegalArgumentException("No member ID was specified.");

        try
        {
            setupDatabase();
            
            final ORID networkRid = IdConverter.toRid(networkId);
            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);

            if (network == null)
                throw new ObjectNotFoundException("Network", networkId);
            else if (!hasPermission(new Network(network), Permissions.ADMIN))
                throw new SecurityException("Access denied.");
    
            final IUser user = _orientDbGraph.getVertex(IdConverter.toRid(networkMember.getResourceId()), IUser.class);
            if (user == null)
                throw new ObjectNotFoundException("User", networkMember.getResourceId());
            
            for (INetworkMembership networkMembership : network.getMembers())
            {
                String memberId = IdConverter.toJid((ORID)networkMembership.getMember().asVertex().getId());
                if (memberId.equals(networkMember.getResourceId()))
                {
                    if (countAdminMembers(networkRid) < 2)
                        throw new SecurityException("Cannot change the permissions on the only ADMIN member.");
                    
                    networkMembership.setPermissions(networkMember.getPermissions());
                    _orientDbGraph.getBaseGraph().commit();
                    return;
                }
            }
        }
        catch (ObjectNotFoundException | SecurityException ne)
        {
            throw ne;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Network", networkId);
            
            _logger.error("Failed to update member: " + networkMember.getResourceName() + ".", e);
            _orientDbGraph.getBaseGraph().rollback();
            throw new NdexException("Failed to update member: " + networkMember.getResourceName() + ".");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    /**************************************************************************
    * Updates a network.
    * 
    * @param updatedNetwork
    *            The updated network information.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws SecurityException
    *            The user doesn't have permissions to update the network.
    * @throws NdexException
    *            Failed to update the network in the database.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateNetwork(final Network updatedNetwork) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (updatedNetwork == null)
            throw new IllegalArgumentException("The updated network is empty.");

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(IdConverter.toRid(updatedNetwork.getId()), INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", updatedNetwork.getId());
            else if (!hasPermission(updatedNetwork, Permissions.WRITE))
                throw new SecurityException("Access denied.");

            if (updatedNetwork.getDescription() != null && !updatedNetwork.getDescription().isEmpty())
                network.setDescription(updatedNetwork.getDescription());

            if (updatedNetwork.getIsPublic() != network.getIsPublic())
                network.setIsPublic(updatedNetwork.getIsPublic());

            if (updatedNetwork.getSource() != null && !updatedNetwork.getSource().isEmpty())
                network.setSource(updatedNetwork.getSource());

            if (updatedNetwork.getTitle() != null && !updatedNetwork.getTitle().isEmpty())
                network.setTitle(updatedNetwork.getTitle());

            _orientDbGraph.getBaseGraph().commit();
        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1)
                throw new ObjectNotFoundException("Network", updatedNetwork.getId());
            
            _logger.error("Failed to update network: " + updatedNetwork.getTitle() + ".", e);
            _orientDbGraph.getBaseGraph().rollback();
            throw new NdexException("Failed to update the network.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Saves an uploaded network file. Determines the type of file uploaded,
    * saves the file, and creates a task.
    * 
    * @param ownerId
    *            The ID of the user creating the group.
    * @param newNetwork
    *            The network to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to parse the file, or create the network in the
    *            database.
    **************************************************************************/
    @POST
    @Path("/upload")
    @Consumes("multipart/form-data")
    @Produces("application/json")
    public void uploadNetwork(@MultipartForm UploadedFile uploadedNetwork, @Context HttpServletRequest httpRequest) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (uploadedNetwork == null || uploadedNetwork.getFileData().length < 1)
            throw new IllegalArgumentException("No uploaded network.");

        final File uploadedNetworkPath = new File(Configuration.getInstance().getProperty("Uploaded-Networks-Path"));
        if (!uploadedNetworkPath.exists())
            uploadedNetworkPath.mkdir();

        final File uploadedNetworkFile = new File(uploadedNetworkPath.getAbsolutePath() + "/" + uploadedNetwork.getFilename());
        
        try
        {
            if (!uploadedNetworkFile.exists())
                uploadedNetworkFile.createNewFile();

            final FileOutputStream saveNetworkFile = new FileOutputStream(uploadedNetworkFile);
            saveNetworkFile.write(uploadedNetwork.getFileData());
            saveNetworkFile.flush();
            saveNetworkFile.close();

            setupDatabase();
            
            final IUser taskOwner = _orientDbGraph.getVertex(IdConverter.toRid(this.getLoggedInUser().getId()), IUser.class);
            
            if (uploadedNetwork.getFilename().endsWith(".sif") || uploadedNetwork.getFilename().endsWith(".xbel") ||
                uploadedNetwork.getFilename().endsWith(".xls") || uploadedNetwork.getFilename().endsWith(".xlsx"))
            {
                ITask processNetworkTask = _orientDbGraph.addVertex("class:network", ITask.class);
                processNetworkTask.setDescription("Process uploaded network");
                processNetworkTask.setType(TaskType.PROCESS_UPLOADED_NETWORK);
                processNetworkTask.setOwner(taskOwner);
                processNetworkTask.setPriority(Priority.LOW);
                processNetworkTask.setProgress(0);
                processNetworkTask.setResource(uploadedNetworkFile.getAbsolutePath());
                processNetworkTask.setStartTime(new Date());
                processNetworkTask.setStatus(Status.QUEUED);
                
                taskOwner.addTask(processNetworkTask);
                _orientDbGraph.getBaseGraph().commit();
            }
            else
            {
                uploadedNetworkFile.delete();
                throw new IllegalArgumentException("The uploaded file type is not supported; must be Excel, SIF, OR XBEL.");
            }
        }
        catch (IllegalArgumentException iae)
        {
            throw iae;
        }
        catch (Exception e)
        {
            _logger.error("Failed to process uploaded network: " + uploadedNetwork.getFilename() + ".", e);
            _orientDbGraph.getBaseGraph().rollback();
            throw new NdexException(e.getMessage());
        }
    }
    
    

    private static void addTermAndFunctionalDependencies(final ITerm term, final Set<ITerm> terms)
    {
        if (terms.add(term))
        {
            if (term instanceof IFunctionTerm)
            {
                terms.add(((IFunctionTerm)term).getTermFunc());
                
                for (ITerm iterm : ((IFunctionTerm) term).getTermParameters())
                {
                	if (iterm instanceof IFunctionTerm)
                		 addTermAndFunctionalDependencies((IFunctionTerm) iterm, terms);
                }
            }
        }
    }

    /**************************************************************************
    * Counter the number of administrative members in the network.
    **************************************************************************/
    private long countAdminMembers(final ORID networkRid) throws NdexException
    {
        final List<ODocument> adminCount = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(@rid) from NetworkMembership where in_userNetworks = " + networkRid + " and permissions = 'ADMIN'"));
        if (adminCount == null || adminCount.isEmpty())
            throw new NdexException("Unable to count ADMIN members.");
        
        return (long)adminCount.get(0).field("count");
    }
    
    /**************************************************************************
    * Maps namespaces from network model object to namespaces in the network
    * domain object.
    **************************************************************************/
    private void createNamespaces(final INetwork newNetwork, final Network networkToCreate, final Map<String, VertexFrame> networkIndex)
    {
        for (final Namespace namespace : networkToCreate.getNamespaces().values())
        {
            final INamespace newNamespace = _orientDbGraph.addVertex("class:namespace", INamespace.class);
            newNamespace.setJdexId(namespace.getJdexId());

            final String prefix = namespace.getPrefix();
            if (prefix != null && !prefix.isEmpty())
                newNamespace.setPrefix(prefix);

            newNamespace.setUri(namespace.getUri());
            newNetwork.addNamespace(newNamespace);
            networkIndex.put(namespace.getJdexId(), newNamespace);
        }
    }

    /**************************************************************************
    * Maps terms in network model object to terms in the network domain object
    * 
    * Note that this requires that the list of terms is ordered such that terms
    * may only refer to other terms if those terms come earlier in the list.
    **************************************************************************/
    private void createTerms(final INetwork newNetwork, final Network networkToCreate, final Map<String, VertexFrame> networkIndex)
    {
        for (final Entry<String, Term> termEntry : networkToCreate.getTerms().entrySet())
        {
            final Term term = termEntry.getValue();

            if (term.getTermType() == null || term.getTermType().isEmpty() || term.getTermType().equals("Base"))
            {
                final IBaseTerm newBaseTerm = _orientDbGraph.addVertex("class:baseTerm", IBaseTerm.class);
                newBaseTerm.setName(((BaseTerm)term).getName());
                newBaseTerm.setJdexId(termEntry.getKey());

                String jdexId = ((BaseTerm)term).getNamespace();
                if (jdexId != null && !jdexId.isEmpty())
                {
                    final VertexFrame namespace = networkIndex.get(jdexId);
                    if (namespace != null)
                        newBaseTerm.setTermNamespace((INamespace) namespace);
                }

                newNetwork.addTerm(newBaseTerm);
                networkIndex.put(newBaseTerm.getJdexId(), newBaseTerm);
            }
            else if (term.getTermType().equals("Function"))
            {
                final IFunctionTerm newFunctionTerm = _orientDbGraph.addVertex("class:functionTerm", IFunctionTerm.class);
                newFunctionTerm.setJdexId(termEntry.getKey());

                final VertexFrame function = networkIndex.get(((FunctionTerm)term).getTermFunction());
                if (function != null)
                    newFunctionTerm.setTermFunc((IBaseTerm) function);

                List<ITerm> iParameters = new ArrayList<ITerm>();
                for (Map.Entry<Integer, String> entry : ((FunctionTerm)term).getParameters().entrySet())
                {
                	// All Terms mentioned as parameters are expected to have been found and created
                	// prior to the current term - it is a requirement of a JDEx format file.
                    ITerm parameter = ((ITerm) networkIndex.get(entry.getValue()));
                    if (null != parameter){
                    	iParameters.add(parameter);
                    }
                    
                }
                newFunctionTerm.setTermParameters(iParameters);

                /*
                 * for (Map.Entry<Integer, String> entry : term
                 * .getTermParameters().entrySet())
                 * functionTerm.getTermParameters().put(entry.getKey(), (ITerm)
                 * networkIndex.get(entry.getValue()));
                 */
                newNetwork.addTerm(newFunctionTerm);
                networkIndex.put(newFunctionTerm.getJdexId(), newFunctionTerm);
            }
        }
    }

    private void createNodes(final INetwork newNetwork, final Network networkToCreate, final Map<String, VertexFrame> networkIndex)
    {
        int nodeCount = 0;

        for (final Entry<String, Node> nodeEntry : networkToCreate.getNodes().entrySet())
        {
            final INode node = _orientDbGraph.addVertex("class:node", INode.class);
            node.setJdexId(nodeEntry.getKey());

            nodeCount++;

            final ITerm representedITerm = (ITerm) networkIndex.get(nodeEntry.getValue().getRepresents());
            node.setRepresents(representedITerm);

            newNetwork.addNdexNode(node);
            networkIndex.put(node.getJdexId(), node);
        }

        newNetwork.setNdexNodeCount(nodeCount);
    }

    private void createEdges(final INetwork newNetwork, final Network networkToCreate, final Map<String, VertexFrame> networkIndex)
    {
        int edgeCount = 0;

        for (final Entry<String, Edge> edgeEntry : networkToCreate.getEdges().entrySet())
        {
            final Edge edgeToCreate = edgeEntry.getValue();
            
            final IEdge newEdge = _orientDbGraph.addVertex("class:edge", IEdge.class);
            newEdge.setJdexId(edgeEntry.getKey());
            
            edgeCount++;

            final INode subjectNode = (INode) networkIndex.get(edgeToCreate.getSubjectId());
            newEdge.setSubject(subjectNode);
            
            final IBaseTerm predicateTerm = (IBaseTerm) networkIndex.get(edgeToCreate.getPredicateId());
            newEdge.setPredicate(predicateTerm);
            
            final INode objectNode = (INode) networkIndex.get(edgeToCreate.getObjectId());
            newEdge.setObject(objectNode);

            newNetwork.addNdexEdge(newEdge);
            networkIndex.put(newEdge.getJdexId(), newEdge);
        }
        
        newNetwork.setNdexEdgeCount(edgeCount);
    }

    private void createCitations(final INetwork newNetwork, final Network networkToCreate, final Map<String, VertexFrame> networkIndex)
    {
        for (final Entry<String, Citation> citationEntry : networkToCreate.getCitations().entrySet())
        {
            final Citation citation = citationEntry.getValue();

            final ICitation newCitation = _orientDbGraph.addVertex("class:citation", ICitation.class);
            newCitation.setJdexId(citationEntry.getKey());
            newCitation.setTitle(citation.getTitle());
            newCitation.setIdentifier(citation.getIdentifier());
            newCitation.setType(citation.getType());
            newCitation.setContributors(citation.getContributors());

            for (final String edgeId : citation.getEdges())
                newCitation.addNdexEdge((IEdge) networkIndex.get(edgeId));

            newNetwork.addCitation(newCitation);
            networkIndex.put(newCitation.getJdexId(), newCitation);
        }
    }

    private void createSupports(final INetwork newNetwork, final Network networkToCreate, final Map<String, VertexFrame> networkIndex)
    {
        for (final Entry<String, Support> supportEntry : networkToCreate.getSupports().entrySet())
        {
            final Support support = supportEntry.getValue();

            final ISupport newSupport = _orientDbGraph.addVertex("class:support", ISupport.class);
            newSupport.setJdexId(supportEntry.getKey());
            newSupport.setText(support.getText());

            for (final String edgeId : support.getEdges())
                newSupport.addNdexEdge((IEdge) networkIndex.get(edgeId));

            if (support.getCitationId() != null && !support.getCitationId().isEmpty())
                newSupport.setSupportCitation((ICitation) networkIndex.get(support.getCitationId()));

            newNetwork.addSupport(newSupport);
            networkIndex.put(newSupport.getJdexId(), newSupport);
        }
    }
    
    private List<Term> getBaseTermsByName(INetwork network, String baseTermName) throws NdexException
    {
        final List<Term> foundTerms = new ArrayList<Term>();
        for (final ITerm networkTerm : network.getTerms())
        {
            if (networkTerm instanceof IBaseTerm)
            {
                if (baseTermName.equals(((IBaseTerm)networkTerm).getName()))
                {
                    final Term term = new BaseTerm((IBaseTerm) networkTerm);
                    foundTerms.add(term);
                }
            }
        }

        return foundTerms;
    }

    private static Network getNetworkBasedOnFoundEdges(final List<IEdge> foundEdges, final INetwork network)
    {
        final Set<INode> requiredINodes = getEdgeNodes(foundEdges);
        final Set<ITerm> requiredITerms = getEdgeTerms(foundEdges, requiredINodes);
        final Set<ISupport> requiredISupports = getEdgeSupports(foundEdges);
        final Set<ICitation> requiredICitations = getEdgeCitations(foundEdges, requiredISupports);
        final Set<INamespace> requiredINamespaces = getTermNamespaces(requiredITerms);

        //Now create the output network
        final Network networkByEdges = new Network();
        networkByEdges.setFormat(network.getFormat());
        networkByEdges.setSource(network.getSource());
        networkByEdges.setTitle(network.getTitle());

        for (final IEdge edge : foundEdges)
            networkByEdges.getEdges().put(edge.getJdexId(), new Edge(edge));

        for (final INode node : requiredINodes)
            networkByEdges.getNodes().put(node.getJdexId(), new Node(node));

        for (final ITerm term : requiredITerms)
        {
            if (term instanceof IBaseTerm)
                networkByEdges.getTerms().put(term.getJdexId(), new BaseTerm((IBaseTerm)term));
            else if (term instanceof IFunctionTerm)
                networkByEdges.getTerms().put(term.getJdexId(), new FunctionTerm((IFunctionTerm)term));
        }

        for (final INamespace namespace : requiredINamespaces)
            networkByEdges.getNamespaces().put(namespace.getJdexId(), new Namespace(namespace));

        for (final ISupport support : requiredISupports)
            networkByEdges.getSupports().put(support.getJdexId(), new Support(support));

        for (final ICitation citation : requiredICitations)
            networkByEdges.getCitations().put(citation.getJdexId(), new Citation(citation));

        return networkByEdges;
    }

    private static Network getNetworkBasedOnFoundNodes(final List<INode> foundINodes, final INetwork network)
    {
        final Set<ITerm> requiredITerms = getNodeTerms(foundINodes);
        final Set<INamespace> requiredINamespaces = getTermNamespaces(requiredITerms);

        //Now create the output network
        final Network networkByNodes = new Network();

        networkByNodes.setFormat(network.getFormat());
        networkByNodes.setSource(network.getSource());
        networkByNodes.setTitle(network.getTitle());

        for (final INode node : foundINodes)
            networkByNodes.getNodes().put(node.getJdexId(), new Node(node));

        for (final ITerm term : requiredITerms)
        {
            if (term instanceof IBaseTerm)
                networkByNodes.getTerms().put(term.getJdexId(), new BaseTerm((IBaseTerm) term));
            else if (term instanceof IFunctionTerm)
                networkByNodes.getTerms().put(term.getJdexId(), new FunctionTerm((IFunctionTerm) term));
        }

        for (final INamespace requiredNamespace : requiredINamespaces)
            networkByNodes.getNamespaces().put(requiredNamespace.getJdexId(), new Namespace(requiredNamespace));

        return networkByNodes;
    }

    private static Set<INode> getEdgeNodes(final List<IEdge> edges)
    {
        final Set<INode> edgeNodes = new HashSet<INode>();
        
        for (final IEdge edge : edges)
        {
            edgeNodes.add(edge.getSubject());
            edgeNodes.add(edge.getObject());
        }

        return edgeNodes;
    }

    private static Set<ITerm> getEdgeTerms(final List<IEdge> edges, final Collection<INode> nodes)
    {
        final Set<ITerm> edgeTerms = new HashSet<ITerm>();
        
        for (final IEdge edge : edges)
            edgeTerms.add(edge.getPredicate());

        for (final INode node : nodes)
        {
            if (node.getRepresents() != null)
                addTermAndFunctionalDependencies(node.getRepresents(), edgeTerms);
        }

        return edgeTerms;
    }

    private static Set<ITerm> getNodeTerms(final Collection<INode> nodes)
    {
        final Set<ITerm> nodeTerms = new HashSet<ITerm>();

        for (final INode node : nodes)
        {
            if (node.getRepresents() != null)
                addTermAndFunctionalDependencies(node.getRepresents(), nodeTerms);
        }

        return nodeTerms;
    }

    private static Set<ISupport> getEdgeSupports(final List<IEdge> edges)
    {
        final Set<ISupport> edgeSupports = new HashSet<ISupport>();
        
        for (final IEdge edge : edges)
        {
            for (final ISupport support : edge.getSupports())
                edgeSupports.add(support);
        }

        return edgeSupports;
    }

    private static Set<ICitation> getEdgeCitations(final List<IEdge> edges, final Collection<ISupport> supports)
    {
        final Set<ICitation> edgeCitations = new HashSet<ICitation>();
        for (final IEdge edge : edges)
        {
            for (final ICitation citation : edge.getCitations())
                edgeCitations.add(citation);
        }

        for (final ISupport support : supports)
        {
            if (support.getSupportCitation() != null)
                edgeCitations.add(support.getSupportCitation());
        }

        return edgeCitations;
    }

    private static Set<INamespace> getTermNamespaces(final Set<ITerm> requiredITerms)
    {
        final Set<INamespace> namespaces = new HashSet<INamespace>();
        
        for (final ITerm term : requiredITerms)
        {
            if (term instanceof IBaseTerm && ((IBaseTerm) term).getTermNamespace() != null)
                namespaces.add(((IBaseTerm) term).getTermNamespace());
        }
        
        return namespaces;
    }
    
    /**************************************************************************
    * Determines if the logged in user has sufficient permissions to a network. 
    * 
    * @param targetNetwork
    *            The network to test for permissions.
    * @return True if the member has permission, false otherwise.
    **************************************************************************/
    private boolean hasPermission(Network targetNetwork, Permissions requiredPermissions)
    {
        for (Membership networkMembership : this.getLoggedInUser().getNetworks())
        {
            if (networkMembership.getResourceId().equals(targetNetwork.getId()) && networkMembership.getPermissions().compareTo(requiredPermissions) > -1)
                return true;
        }
        
        return false;
    }

    private List<IEdge> neighborhoodQuery(final INetwork network, final NetworkQueryParameters queryParameters) throws IllegalArgumentException
    {
        final List<IEdge> foundEdges = new ArrayList<IEdge>();

        //Make a SearchSpec based on the NetworkQueryParameters, verifying &
        //converting term ids in the process
        final SearchSpec searchSpec = new SearchSpec(queryParameters);
        
        final Set<OrientVertex> orientEdges = NetworkQueries.INSTANCE.searchNeighborhoodByTerm(_orientDbGraph.getBaseGraph(), (OrientVertex) network.asVertex(), searchSpec);
        for (final OrientVertex edge : orientEdges)
            foundEdges.add(_orientDbGraph.getVertex(edge, IEdge.class));

        return foundEdges;
    }
}
