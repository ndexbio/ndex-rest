package org.ndexbio.rest.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.security.PermitAll;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import org.jboss.resteasy.client.exception.ResteasyAuthenticationException;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.exceptions.ValidationException;
import org.ndexbio.rest.gremlin.NetworkQueries;
import org.ndexbio.rest.gremlin.SearchSpec;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Membership;
import org.ndexbio.rest.models.Namespace;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.NetworkQueryParameters;
import org.ndexbio.rest.models.SearchParameters;
import org.ndexbio.rest.models.SearchResult;
import org.ndexbio.rest.models.Term;
import org.ndexbio.rest.models.BaseTerm;
import org.ndexbio.rest.models.FunctionTerm;
import org.ndexbio.rest.models.Node;
import org.ndexbio.rest.models.Edge;
import org.ndexbio.rest.models.Citation;
import org.ndexbio.rest.models.Support;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import com.tinkerpop.frames.VertexFrame;

//TODO: Need to add a method to change a member's permissions
@Path("/networks")
public class NetworkService extends NdexService
{
    /**************************************************************************
    * Execute parent default constructor to initialize OrientDB.
    **************************************************************************/
    public NetworkService()
    {
        super();
    }



    /**************************************************************************
    * Suggests terms that start with the partial term.
    * 
    * @param networkId
    *            The network ID.
    * @param partialTerm
    *            The partially entered term.
    * @return A collection of terms that start with the partial term.
    **************************************************************************/
    @GET
    @Path("/{networkId}/autosuggest/{partialTerm}")
    @Produces("application/json")
    public Collection<String> autoSuggestTerms(@PathParam("networkId")final String networkId, @PathParam("partialTerm")final String partialTerm) throws Exception
    {
        final ORID networkRid = RidConverter.convertToRid(networkId);

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
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
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Network createNetwork(final Network newNetwork) throws Exception
    {
        //TODO: check that member has ADMIN and handle groups as well as users
        if (newNetwork == null)
            throw new ValidationException("The network to create is null.");
        else if (newNetwork.getMembers() == null || newNetwork.getMembers().size() == 0)
            throw new ValidationException("The network to create has no members specified.");

        try
        {
            setupDatabase();

            final Membership newNetworkMembership = newNetwork.getMembers().get(0);

            final ORID userRid = RidConverter.convertToRid(newNetworkMembership.getResourceId());

            final IUser networkOwner = _orientDbGraph.getVertex(userRid, IUser.class);
            if (networkOwner == null)
                throw new ObjectNotFoundException("User", newNetworkMembership.getResourceId());

            final Map<String, VertexFrame> networkIndex = new HashMap<String, VertexFrame>();

            final INetwork network = _orientDbGraph.addVertex("class:network", INetwork.class);

            final INetworkMembership membership = _orientDbGraph.addVertex("class:networkMembership", INetworkMembership.class);
            membership.setPermissions(Permissions.ADMIN);
            membership.setMember(networkOwner);
            membership.setNetwork(network);

            networkOwner.addNetwork(membership);
            network.addMember(membership);

            network.setIsPublic(false);
            network.setFormat(newNetwork.getFormat());
            network.setSource(newNetwork.getSource());
            network.setTitle(newNetwork.getTitle());

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
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback();
            throw e;
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
    **************************************************************************/
    @DELETE
    @Path("/{networkId}")
    @Produces("application/json")
    public void deleteNetwork(@PathParam("networkId")final String networkJid) throws NdexException
    {
        if (networkJid == null || networkJid.isEmpty())
            throw new ValidationException("No network ID was specified.");

        ORID networkRid = RidConverter.convertToRid(networkJid);

        try
        {
            setupDatabase();

            final INetwork networkToDelete = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (networkToDelete == null)
                throw new ObjectNotFoundException("Network", networkJid);

            final List<ODocument> adminCount = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Membership where in_members = ? and permissions = 'ADMIN'"));
            if (adminCount == null || adminCount.isEmpty())
                throw new NdexException("Unable to count ADMIN members.");
            else if ((long)adminCount.get(0).field("count") > 1)
                throw new NdexException("Cannot delete a network that contains other ADMIN members.");

            for (INetworkMembership networkMembership : networkToDelete.getMembers())
                _orientDbGraph.removeVertex(networkMembership.asVertex());

            final List<ODocument> networkChildren = _ndexDatabase.query(new OSQLSynchQuery<Object>("select @rid from (traverse * from " + networkRid + " while @class <> 'Account')"));
            for (ODocument networkChild : networkChildren)
            {
                final ORID childId = networkChild.field("rid", OType.LINK);

                final OrientElement element = _orientDbGraph.getBaseGraph().getElement(childId);
                if (element != null)
                    element.remove();
            }
            
            _orientDbGraph.removeVertex(networkToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
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
    * @param search
    *            The search terms.
    * @param offset
    *            The offset.
    * @param limit
    *            The number of items to retrieve.
    **************************************************************************/
    @POST
    @PermitAll
    @Path("/search")
    @Produces("application/json")
    public SearchResult<Network> findNetworks(SearchParameters searchParameters) throws NdexException
    {
        if (searchParameters.getSearchString() == null || searchParameters.getSearchString().isEmpty())
            throw new ValidationException("No search string was specified.");
        else
            searchParameters.setSearchString(searchParameters.getSearchString().toUpperCase().trim());

        final List<Network> foundNetworks = new ArrayList<Network>();

        final SearchResult<Network> result = new SearchResult<Network>();
        result.setResults(foundNetworks);

        // TODO: Remove these, unnecessary
        result.setPageSize(searchParameters.getTop());
        result.setSkip(searchParameters.getSkip());

        final int startIndex = searchParameters.getSkip() * searchParameters.getTop();

        final String whereClause = " where title.toUpperCase() like '%" + searchParameters.getSearchString() + "%' OR description.toUpperCase() like '%" + searchParameters.getSearchString() + "%'";

        final String query = "select from Network " + whereClause + " order by creation_date desc skip " + startIndex + " limit " + searchParameters.getTop();

        try
        {
            setupDatabase();

            final List<ODocument> networkDocumentList = _orientDbGraph.getBaseGraph().getRawGraph().query(new OSQLSynchQuery<ODocument>(query));

            for (final ODocument document : networkDocumentList)
                foundNetworks.add(new Network(_orientDbGraph.getVertex(document, INetwork.class)));

            result.setResults(foundNetworks);

            return result;
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
    **************************************************************************/
    @GET
    @Path("/{networkId}")
    @Produces("application/json")
    public Network getNetwork(@PathParam("networkId")final String networkJid) throws NdexException
    {
        if (networkJid == null || networkJid.isEmpty())
            throw new ValidationException("No network ID was specified.");

        final ORID networkRid = RidConverter.convertToRid(networkJid);

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", networkJid);
            else if (!network.getIsPublic())
            {
                for (Membership userMembership : this.getLoggedInUser().getNetworks())
                {
                    if (userMembership.getResourceId().equals(networkJid))
                        return new Network(network);
                }
                
                throw new ResteasyAuthenticationException("You do not have access to that network.");
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
    * Gets a page of Edges for the specified network, along with the supporting
    * nodes, terms, namespaces, supports, and citations.
    * 
    * @param networkId
    *            The network ID.
    **************************************************************************/
    @GET
    @Path("/{networkId}/edges/{skip}/{top}")
    @Produces("application/json")
    public Network getEdges(@PathParam("networkId")final String networkJid, @PathParam("skip")final int skip, @PathParam("top")final int top) throws NdexException
    {
        if (networkJid == null || networkJid.isEmpty())
            throw new ValidationException("No network ID was specified.");
        else if (top < 1)
            throw new IllegalArgumentException("Number of results to return is less than 1.");

        final ORID networkRid = RidConverter.convertToRid(networkJid);

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", networkJid);

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
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Get a subnetwork network based on network query parameters
    * 
    * @param queryParameters
    **************************************************************************/
    @POST
    @Path("/{networkId}/query")
    @Produces("application/json")
    public Network queryNetwork(@PathParam("networkId")final String networkJid, final NetworkQueryParameters queryParameters) throws Exception
    {
        final ORID networkRid = RidConverter.convertToRid(networkJid);

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(networkRid, INetwork.class);
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
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateNetwork(final Network updatedNetwork) throws NdexException
    {
        if (updatedNetwork == null)
            throw new ValidationException("The updated network is empty.");

        try
        {
            setupDatabase();

            final INetwork network = _orientDbGraph.getVertex(RidConverter.convertToRid(updatedNetwork.getId()), INetwork.class);
            if (network == null)
                throw new ObjectNotFoundException("Network", updatedNetwork.getId());
            
            //TODO: Don't allow the only ADMIN member to change their own permissions

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
        catch (Exception e)
        {
            _orientDbGraph.getBaseGraph().rollback();
            throw e;
        }
        finally
        {
            teardownDatabase();
        }
    }

    

    private static void addTermAndFunctionalDependencies(final ITerm term, final Set<ITerm> terms)
    {
        if (terms.add(term))
        {
            if (term instanceof IFunctionTerm)
            {
                terms.add(((IFunctionTerm) term).getTermFunction());
                
                for (ITerm parameterTerm : ((IFunctionTerm) term).getTermParameters().values())
                    addTermAndFunctionalDependencies(parameterTerm, terms);
            }
        }
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
                        newBaseTerm.setNamespace((INamespace) namespace);
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
                    newFunctionTerm.setTermFunction((IBaseTerm) function);

                for (Map.Entry<Integer, String> entry : ((FunctionTerm)term).getParameters().entrySet())
                {
                    Integer key = entry.getKey();
                    String value = entry.getValue();
                    newFunctionTerm.getTextParameters().put(key, value);
                }

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
                newSupport.setCitation((ICitation) networkIndex.get(support.getCitationId()));

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
                networkByEdges.getTerms().put(term.getJdexId(), new BaseTerm((IBaseTerm) term));
            else if (term instanceof IFunctionTerm)
                networkByEdges.getTerms().put(term.getJdexId(), new FunctionTerm((IFunctionTerm) term));
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
            if (support.getCitation() != null)
                edgeCitations.add(support.getCitation());
        }

        return edgeCitations;
    }

    private static Set<INamespace> getTermNamespaces(final Set<ITerm> requiredITerms)
    {
        final Set<INamespace> namespaces = new HashSet<INamespace>();
        
        for (final ITerm term : requiredITerms)
        {
            if (term instanceof IBaseTerm && ((IBaseTerm) term).getNamespace() != null)
                namespaces.add(((IBaseTerm) term).getNamespace());
        }
        
        return namespaces;
    }

    private List<IEdge> neighborhoodQuery(final INetwork network, final NetworkQueryParameters queryParameters) throws ValidationException
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
