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
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import com.tinkerpop.frames.VertexFrame;

//TODO: Need to add methods to add/remove members
//TODO: Need to add a method to change a member's permissions
@Path("/networks")
public class NetworkService extends NdexService {
	/**************************************************************************
	 * Execute parent default constructor to initialize OrientDB.
	 **************************************************************************/
	public NetworkService() {
		super();
	}

	/**************************************************************************
	 * Creates a network.
	 * 
	 * @param ownerId
	 *            The ID of the user creating the group.
	 * @param netNetwork
	 *            The network to create.
	 **************************************************************************/
	@PUT
	@Produces("application/json")
	public Network createNetwork(final String ownerId, final Network netNetwork)
			throws Exception {
		if (ownerId == null || ownerId.isEmpty())
			throw new ValidationException("The network owner wasn't specified.");
		else if (netNetwork == null)
			throw new ValidationException("The network to create is empty.");

		ORID userRid = RidConverter.convertToRid(ownerId);

		try {
			setupDatabase();

			final IUser networkOwner = _orientDbGraph.getVertex(userRid,
					IUser.class);
			if (networkOwner == null)
				throw new ObjectNotFoundException("User", ownerId);

			final Map<String, VertexFrame> networkIndex = new HashMap<String, VertexFrame>();

			final INetwork network = _orientDbGraph.addVertex("class:network",
					INetwork.class);

			final INetworkMembership membership = _orientDbGraph.addVertex(
					"class:networkMembership", INetworkMembership.class);
			membership.setPermissions(Permissions.ADMIN);
			membership.setMember(networkOwner);
			membership.setNetwork(network);

			networkOwner.addNetwork(membership);
			network.addMember(membership);

			network.setIsPublic(false);
			network.setFormat(netNetwork.getFormat());
			network.setSource(netNetwork.getSource());
			network.setTitle(netNetwork.getTitle());

			// First create all namespaces used by the network
			createNamespaces(network, netNetwork, networkIndex);

			// Then create terms which may reference the namespaces
			// The terms are created in order of reference - later terms may
			// refer to earlier terms
			createTerms(network, netNetwork, networkIndex);

			// Then create nodes that may reference terms
			createNodes(network, netNetwork, networkIndex);

			// Then create edges that reference terms nodes
			createEdges(network, netNetwork, networkIndex);

			// Then create citations that reference edges
			createCitations(network, netNetwork, networkIndex);

			// Then create supports that reference edges and citations
			createSupports(network, netNetwork, networkIndex);

			_orientDbGraph.getBaseGraph().commit();

			return new Network(network);
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
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
	public void deleteNetwork(@PathParam("networkId") final String networkJid)
			throws NdexException {
		if (networkJid == null || networkJid.isEmpty())
			throw new ValidationException("No network ID was specified.");

		ORID networkRid = RidConverter.convertToRid(networkJid);

		try {
			setupDatabase();

			final Vertex networkToDelete = _orientDbGraph.getVertex(networkRid);
			if (networkToDelete == null)
				return;

			// TODO: Need to remove orphaned vertices
			_orientDbGraph.removeVertex(networkToDelete);
			_orientDbGraph.getBaseGraph().commit();

			// TODO: Deleting a network should delete all children
			// ODatabaseDocumentTx databaseDocumentTx =
			// orientDbGraph.getBaseGraph().getRawGraph();
			// List<ODocument> networkChildren = databaseDocumentTx.query(new
			// OSQLSynchQuery<Object>("select @rid from (TRAVERSE * FROM " +
			// networkRid + " while @class <> 'xUser')"));
			//
			// for (ODocument networkChild : networkChildren)
			// {
			// ORID childId = networkChild.field("rid", OType.LINK);
			// OrientElement element =
			// orientDbGraph.getBaseGraph().getElement(childId);
			//
			// if (element != null)
			// element.remove();
			// }
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
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
	@Path("/search")
	@Produces("application/json")
	public SearchResult<Network> findNetworks(SearchParameters searchParameters)
			throws NdexException {
		if (searchParameters.getSearchString() == null
				|| searchParameters.getSearchString().isEmpty())
			throw new ValidationException("No search string was specified.");
		else
			searchParameters.setSearchString(searchParameters.getSearchString()
					.toUpperCase().trim());

		final List<Network> foundNetworks = new ArrayList<Network>();

		final SearchResult<Network> result = new SearchResult<Network>();
		result.setResults(foundNetworks);

		// TODO: Remove these, unnecessary
		result.setPageSize(searchParameters.getTop());
		result.setSkip(searchParameters.getSkip());

		final int startIndex = searchParameters.getSkip()
				* searchParameters.getTop();

		final String whereClause = " where title.toUpperCase() like '%"
				+ searchParameters.getSearchString()
				+ "%' OR description.toUpperCase() like '%"
				+ searchParameters.getSearchString() + "%'";

		final String query = "select from Network " + whereClause
				+ " order by creation_date desc skip " + startIndex + " limit "
				+ searchParameters.getTop();

		try {
			setupDatabase();

			final List<ODocument> networkDocumentList = _orientDbGraph
					.getBaseGraph().getRawGraph()
					.query(new OSQLSynchQuery<ODocument>(query));

			for (final ODocument document : networkDocumentList)
				foundNetworks.add(new Network(_orientDbGraph.getVertex(
						document, INetwork.class)));

			result.setResults(foundNetworks);

			return result;
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
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
	@PermitAll
	@Path("/{networkId}")
	@Produces("application/json")
	public Network getNetwork(@PathParam("networkId") final String networkJid)
			throws NdexException {
		if (networkJid == null || networkJid.isEmpty())
			throw new ValidationException("No network ID was specified.");

		final ORID networkRid = RidConverter.convertToRid(networkJid);

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				return null;
			else
				return new Network(network);
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
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
	public Network getEdges(@PathParam("networkId") final String networkJid,
			int skip, int top) throws NdexException {
		if (networkJid == null || networkJid.isEmpty())
			throw new ValidationException("No network ID was specified.");

		final ORID networkRid = RidConverter.convertToRid(networkJid);

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkJid);

			int counter = 0;
			final List<IEdge> foundIEdges = new ArrayList<IEdge>();
			final int startIndex = skip * top;

			for (final IEdge networkEdge : network.getNdexEdges()) {
				if (counter >= startIndex)
					foundIEdges.add(networkEdge);

				counter++;
				if (counter >= startIndex + top)
					break;
			}

			return getNetworkBasedOnFoundEdges(foundIEdges, network);
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets a page of Nodes for the specified network, along with the supporting
	 * edges, terms, namespaces, supports, and citations.
	 * 
	 * @param networkId
	 *            The network ID.
	 **************************************************************************/
	@GET
	@Path("/{networkId}/nodes")
	@Produces("application/json")
	public Network getNodes(@PathParam("networkId") final String networkJid,
			Integer limit, Integer offset) throws NdexException {
		if (networkJid == null || networkJid.isEmpty())
			throw new ValidationException("No network ID was specified.");

		final ORID networkRid = RidConverter.convertToRid(networkJid);
		final INetwork network = _orientDbGraph.getVertex(networkRid,
				INetwork.class);
		if (network == null)
			throw new ObjectNotFoundException("Network", networkJid);

		int counter = 0;
		final List<INode> foundINodes = new ArrayList<INode>();
		int startIndex = limit * offset;

		try {
			setupDatabase();

			for (final INode networkNode : network.getNdexNodes()) {
				if (counter >= startIndex)
					foundINodes.add(networkNode);

				counter++;
				if (counter >= startIndex + limit)
					break;
			}

			return getNetworkBasedOnFoundNodes(foundINodes, network);
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
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
	public void updateNetwork(final Network updatedNetwork)
			throws NdexException {
		if (updatedNetwork == null)
			throw new ValidationException("The updated network is empty.");

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(
					RidConverter.convertToRid(updatedNetwork.getId()),
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network",
						updatedNetwork.getId());

			network.setSource(updatedNetwork.getSource());
			network.setTitle(updatedNetwork.getTitle());

			_orientDbGraph.getBaseGraph().commit();
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Get a subnetwork network based on network query parameters
	 * 
	 * @param queryParameters
	 **************************************************************************/
	@POST
	@Path("/search")
	@Produces("application/json")
	public Network queryNetwork(
			@PathParam("networkId") final String networkJid,
			NetworkQueryParameters queryParameters) throws ValidationException {
		try {
			setupDatabase();

			ORID networkRid = RidConverter.convertToRid(networkJid);
			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null) {
				return null;
			} else {
				List<IEdge> foundIEdges = neighborhoodQuery(network,
						queryParameters);
				return getNetworkBasedOnFoundEdges(foundIEdges, network);
			}
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Get BaseTerm from a network matching name
	 * 
	 * @param baseTermName
	 **************************************************************************/
	@GET
	@Path("/{networkId}/baseTerms")
	@Produces("application/json")
	public Collection<Term> getBaseTermsByName(
			@PathParam("networkId") final String networkJid, String baseTermName)
			throws NdexException {
		try {
			setupDatabase();

			ORID networkRid = RidConverter.convertToRid(networkJid);
			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);

			if (network == null) {
				return null;
			} else {
				Collection<Term> foundTerms = new ArrayList<Term>();
				for (ITerm networkTerm : network.getTerms()) {
					if (networkTerm instanceof IBaseTerm) {
						if (baseTermName.equals(((IBaseTerm) networkTerm)
								.getName())) {
							Term term = new BaseTerm((IBaseTerm)networkTerm);
							foundTerms.add(term);
						}
					}
				}

				return foundTerms;
			}
		} catch (Exception e) {
			_orientDbGraph.getBaseGraph().rollback();
			throw e;
		} finally {
			teardownDatabase();
		}
	}

	private List<IEdge> neighborhoodQuery(INetwork network,
			NetworkQueryParameters queryParameters) throws ValidationException {
		List<IEdge> foundEdges = new ArrayList<IEdge>();
		// Make a SearchSpec based on the NetworkQueryParameters, verifying &
		// converting term ids in the process
		SearchSpec searchSpec = new SearchSpec(queryParameters);
		Set<OrientVertex> orientEdges = NetworkQueries.INSTANCE
				.searchNeighborhoodByTerm(_orientDbGraph.getBaseGraph(),
						(OrientVertex) network.asVertex(), searchSpec);
		for (OrientVertex edge : orientEdges) {
			foundEdges.add(_orientDbGraph.getVertex(edge, IEdge.class));
		}
		return foundEdges;
	}

	/*
	 * map namespaces from network model object to namespaces in the network
	 * domain object
	 */
	private void createNamespaces(INetwork newNetwork, Network networkToCreate,
			Map<String, VertexFrame> networkIndex) {
		for (final Namespace namespace : networkToCreate.getNamespaces()
				.values()) {
			final INamespace newNamespace = _orientDbGraph.addVertex(
					"class:namespace", INamespace.class);
			newNamespace.setJdexId(namespace.getJdexId());

			final String prefix = namespace.getPrefix();
			if (prefix != null && !prefix.isEmpty())
				newNamespace.setPrefix(prefix);

			newNamespace.setUri(namespace.getUri());
			newNetwork.addNamespace(newNamespace);
			networkIndex.put(namespace.getJdexId(), newNamespace);
		}
	}

	/*
	 * map terms in network model object to terms in the network domain object
	 * 
	 * Note that this requires that the list of terms is ordered such that terms
	 * may only refer to other terms if those terms come earlier in the list.
	 * 
	 * mod 25Nov2013 use JdexIds for reference in lieu of object aggregation
	 */
	private void createTerms(INetwork newNetwork, Network networkToCreate,
			Map<String, VertexFrame> networkIndex) {
		for (final Entry<String, Term> termEntry : networkToCreate.getTerms()
				.entrySet()) {
			final Term term = termEntry.getValue();

			if (term.getTermType().equals("BASE")) {
				final IBaseTerm iBaseTerm = _orientDbGraph.addVertex(
						"class:baseTerm", IBaseTerm.class);
				iBaseTerm.setName(term.getName());
				iBaseTerm.setJdexId(termEntry.getKey());

				String jdexId = term.getNamespace();
				if (jdexId != null && !jdexId.isEmpty()) {
					final VertexFrame namespace = networkIndex.get(jdexId);
					if (namespace != null)
						iBaseTerm.setNamespace((INamespace) namespace);
				}

				newNetwork.addTerm(iBaseTerm);
				networkIndex.put(iBaseTerm.getJdexId(), iBaseTerm);
			} else if (term.getTermType().equals("FUNCTION")) {

				final IFunctionTerm functionTerm = _orientDbGraph.addVertex(
						"class:functionTerm", IFunctionTerm.class);
				functionTerm.setJdexId(termEntry.getKey());

				final VertexFrame function = networkIndex.get(term
						.getTermFunction());
				if (function != null)
					functionTerm.setTermFunction((IBaseTerm) function);

				for (Map.Entry<Integer, String> entry : term.getParameters()
						.entrySet()) {
					Integer key = entry.getKey();
					String value = entry.getValue();
					functionTerm.getTextParameters().put(key, value);
				}

				/*
				 * for (Map.Entry<Integer, String> entry : term
				 * .getTermParameters().entrySet())
				 * functionTerm.getTermParameters().put(entry.getKey(), (ITerm)
				 * networkIndex.get(entry.getValue()));
				 */
				newNetwork.addTerm(functionTerm);
				networkIndex.put(functionTerm.getJdexId(), functionTerm);
			}
		}
	}

	private void createNodes(INetwork newNetwork, Network networkToCreate,
			Map<String, VertexFrame> networkIndex) {
		int nodeCount = 0;

		for (final Entry<String, Node> nodeEntry : networkToCreate.getNodes()
				.entrySet()) {
			final INode node = _orientDbGraph.addVertex("class:node",
					INode.class);
			node.setJdexId(nodeEntry.getKey());

			nodeCount++;

			final ITerm representedITerm = (ITerm) networkIndex.get(nodeEntry
					.getValue().getRepresents());
			node.setRepresents(representedITerm);

			newNetwork.addNdexNode(node);
			networkIndex.put(node.getJdexId(), node);
		}

		newNetwork.setNdexNodeCount(nodeCount);
	}

	private void createEdges(INetwork newNetwork, Network networkToCreate,
			Map<String, VertexFrame> networkIndex) {
		int edgeCount = 0;

		for (final Entry<String, Edge> edgeEntry : networkToCreate.getEdges()
				.entrySet()) {
			Edge edge = edgeEntry.getValue();
			IEdge iEdge = _orientDbGraph.addVertex("class:edge", IEdge.class);
			iEdge.setJdexId(edgeEntry.getKey());
			edgeCount++;

			INode subjectINode = (INode) networkIndex.get(edge.getSubjectId());
			iEdge.setSubject(subjectINode);
			IBaseTerm predicateITerm = (IBaseTerm) networkIndex.get(edge
					.getPredicateId());
			iEdge.setPredicate(predicateITerm);
			INode objectINode = (INode) networkIndex.get(edge.getObjectId());
			iEdge.setObject(objectINode);

			newNetwork.addNdexEdge(iEdge);
			networkIndex.put(iEdge.getJdexId(), iEdge);
		}
		newNetwork.setNdexEdgeCount(edgeCount);

	}

	private void createCitations(INetwork newNetwork, Network networkToCreate,
			Map<String, VertexFrame> networkIndex) {
		for (final Entry<String, Citation> citationEntry : networkToCreate
				.getCitations().entrySet()) {
			final Citation citation = citationEntry.getValue();

			final ICitation newCitation = _orientDbGraph.addVertex(
					"class:citation", ICitation.class);
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

	private void createSupports(INetwork newNetwork, Network networkToCreate,
			Map<String, VertexFrame> networkIndex) {
		for (final Entry<String, Support> supportEntry : networkToCreate
				.getSupports().entrySet()) {
			final Support support = supportEntry.getValue();

			final ISupport newSupport = _orientDbGraph.addVertex(
					"class:support", ISupport.class);
			newSupport.setJdexId(supportEntry.getKey());
			newSupport.setText(support.getText());

			for (final String edgeId : support.getEdges())
				newSupport.addNdexEdge((IEdge) networkIndex.get(edgeId));

			if (support.getCitationId() != null
					&& !support.getCitationId().isEmpty())
				newSupport.setCitation((ICitation) networkIndex.get(support
						.getCitationId()));

			newNetwork.addSupport(newSupport);
			networkIndex.put(newSupport.getJdexId(), newSupport);
		}
	}

	private static Network getNetworkBasedOnFoundEdges(List<IEdge> foundEdges,
			INetwork network) {
		Set<INode> requiredINodes = getEdgeNodes(foundEdges);
		Set<ITerm> requiredITerms = getEdgeTerms(foundEdges, requiredINodes);
		Set<ISupport> requiredISupports = getEdgeSupports(foundEdges);
		Set<ICitation> requiredICitations = getEdgeCitations(foundEdges,
				requiredISupports);
		Set<INamespace> requiredINamespaces = getTermNamespaces(requiredITerms);

		// Now create the output network
		Network networkByEdges = new Network();
		networkByEdges.setFormat(network.getFormat());
		networkByEdges.setSource(network.getSource());
		networkByEdges.setTitle(network.getTitle());

		for (IEdge edge : foundEdges) {
			networkByEdges.getEdges().put(edge.getJdexId(), new Edge(edge));
		}

		for (INode node : requiredINodes) {
			networkByEdges.getNodes().put(node.getJdexId(), new Node(node));
		}

		for (ITerm term : requiredITerms) {
			if (term instanceof IBaseTerm) {
				networkByEdges.getTerms().put(term.getJdexId(),
						new BaseTerm((IBaseTerm) term));
			} else if (term instanceof IFunctionTerm) {
				networkByEdges.getTerms().put(term.getJdexId(),
						new FunctionTerm((IFunctionTerm) term));
			}
		}

		for (INamespace ns : requiredINamespaces) {
			networkByEdges.getNamespaces().put(ns.getJdexId(),
					new Namespace(ns));
		}

		for (ISupport support : requiredISupports) {
			networkByEdges.getSupports().put(support.getJdexId(),
					new Support(support));
		}

		for (ICitation citation : requiredICitations) {
			networkByEdges.getCitations().put(citation.getJdexId(),
					new Citation(citation));
		}
		return networkByEdges;
	}

	private static Network getNetworkBasedOnFoundNodes(List<INode> foundINodes,
			INetwork network) {
		Set<ITerm> requiredITerms = getNodeTerms(foundINodes);
		Set<INamespace> requiredINamespaces = getTermNamespaces(requiredITerms);

		// Now create the output network
		Network networkByNodes = new Network();

		networkByNodes.setFormat(network.getFormat());
		networkByNodes.setSource(network.getSource());
		networkByNodes.setTitle(network.getTitle());

		for (INode node : foundINodes) {
			networkByNodes.getNodes().put(node.getJdexId(), new Node(node));
		}

		for (ITerm term : requiredITerms) {
			if (term instanceof IBaseTerm) {
				networkByNodes.getTerms().put(term.getJdexId(),
						new BaseTerm((IBaseTerm) term));
			} else if (term instanceof IFunctionTerm) {
				networkByNodes.getTerms().put(term.getJdexId(),
						new FunctionTerm((IFunctionTerm) term));
			}

		}

		for (INamespace ns : requiredINamespaces) {
			networkByNodes.getNamespaces().put(ns.getJdexId(),
					new Namespace(ns));
		}

		return networkByNodes;
	}

	private static Set<INode> getEdgeNodes(List<IEdge> edges) {
		Set<INode> edgeNodes = new HashSet<INode>();
		for (IEdge edge : edges) {
			edgeNodes.add(edge.getSubject());
			edgeNodes.add(edge.getObject());
		}

		return edgeNodes;
	}

	private static Set<ITerm> getEdgeTerms(List<IEdge> edges,
			Collection<INode> nodes) {
		Set<ITerm> edgeTerms = new HashSet<ITerm>();
		for (IEdge edge : edges) {
			edgeTerms.add(edge.getPredicate());
		}

		for (INode node : nodes) {
			if (node.getRepresents() != null) {
				addTermAndFunctionalDependencies(node.getRepresents(),
						edgeTerms);
			}
		}

		return edgeTerms;
	}

	private static Set<ITerm> getNodeTerms(Collection<INode> nodes) {
		final Set<ITerm> nodeTerms = new HashSet<ITerm>();

		for (final INode node : nodes) {
			if (node.getRepresents() != null)
				addTermAndFunctionalDependencies(node.getRepresents(),
						nodeTerms);
		}

		return nodeTerms;
	}

	private static void addTermAndFunctionalDependencies(ITerm term,
			Set<ITerm> terms) {
		if (terms.add(term)) {
			if (term instanceof IFunctionTerm) {
				terms.add(((IFunctionTerm) term).getTermFunction());
				for (ITerm parameterTerm : ((IFunctionTerm) term)
						.getTermParameters().values()) {
					addTermAndFunctionalDependencies(parameterTerm, terms);
				}
			}
		}
	}

	private static Set<ISupport> getEdgeSupports(List<IEdge> edges) {
		Set<ISupport> edgeSupports = new HashSet<ISupport>();
		for (IEdge edge : edges) {
			for (ISupport support : edge.getSupports()) {
				edgeSupports.add(support);
			}
		}

		return edgeSupports;
	}

	private static Set<ICitation> getEdgeCitations(List<IEdge> edges,
			Collection<ISupport> supports) {
		Set<ICitation> edgeCitations = new HashSet<ICitation>();
		for (IEdge edge : edges) {
			for (ICitation citation : edge.getCitations()) {
				edgeCitations.add(citation);
			}
		}

		for (ISupport support : supports) {
			if (support.getCitation() != null) {
				edgeCitations.add(support.getCitation());
			}

		}

		return edgeCitations;
	}

	private static Set<INamespace> getTermNamespaces(Set<ITerm> requiredITerms) {
		Set<INamespace> namespaces = new HashSet<INamespace>();
		for (ITerm term : requiredITerms) {
			if (term instanceof IBaseTerm
					&& ((IBaseTerm) term).getNamespace() != null) {
					namespaces.add(((IBaseTerm) term).getNamespace());
			}
		}
		return namespaces;
	}
}
