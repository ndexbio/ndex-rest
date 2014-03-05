package org.ndexbio.rest.services;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import com.tinkerpop.frames.VertexFrame;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.common.exceptions.*;
import org.ndexbio.common.helpers.Configuration;
import org.ndexbio.common.helpers.IdConverter;
import org.ndexbio.common.models.data.*;
import org.ndexbio.common.models.object.*;
import org.ndexbio.orientdb.gremlin.*;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.equivalence.EquivalenceFinder;
import org.ndexbio.rest.equivalence.IdEquivalenceFinder;
import org.ndexbio.rest.gremlin.NetworkQueries;
import org.ndexbio.rest.helpers.TermDependencyComparator;
//import org.ndexbio.rest.gremlin.NetworkQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dextergraphics
 *
 */
@Path("/networks")
public class NetworkService extends NdexService {
	private static final Logger _logger = LoggerFactory
			.getLogger(NetworkService.class);

	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public NetworkService(@Context HttpServletRequest httpRequest) {
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
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return A collection of terms that start with the partial term.
	 **************************************************************************/
	@GET
	@Path("/{networkId}/autosuggest/{partialTerm}")
	@Produces("application/json")
	@ApiDoc("Find base terms whose names complete the partial term name partialTerm. Error if the network does not exist or is not specified")
	public Collection<String> autoSuggestTerms(
			@PathParam("networkId") final String networkId,
			@PathParam("partialTerm") final String partialTerm)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");
		else if (partialTerm == null || partialTerm.isEmpty()
				|| partialTerm.length() < 3)
			return null;

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(
					IdConverter.toRid(networkId), INetwork.class);
			if (network == null)
				return null;
			else {
				final Collection<String> foundTerms = new ArrayList<String>();
				for (final ITerm networkTerm : network.getTerms()) {
					if (networkTerm instanceof IBaseTerm) {
						if (((IBaseTerm) networkTerm).getName().toLowerCase()
								.startsWith(partialTerm.toLowerCase())) {
							foundTerms.add(((IBaseTerm) networkTerm).getName());

							if (foundTerms.size() == 20)
								return foundTerms;
						}
					}
				}

				return foundTerms;
			}
		} catch (Exception e) {
			_logger.error("Failed to retrieve auto-suggest data for: "
					+ partialTerm + ".", e);
			throw new NdexException(
					"Failed to retrieve auto-suggest data for: " + partialTerm
							+ ".");
		} finally {
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
	 *             Bad input.
	 * @throws DuplicateObjectException
	 *             The user already has a network with the same title.
	 * @throws NdexException
	 *             Failed to create the network in the database.
	 * @return The newly created network.
	 **************************************************************************/
	/*
	 * refactored to support no-transactional database operations
	 */
	@PUT
	@Produces("application/json")
	@ApiDoc("Creates a new network based on JDEx structure. Errors if the JDEx is not provided or if the JDEx does not specify a name.")
	public Network createNetwork(final Network newNetwork)
			throws IllegalArgumentException, DuplicateObjectException,
			NdexException {
		Preconditions
				.checkArgument(null != newNetwork, "A network is required");
		Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newNetwork.getName()),
				"A network name is required");

		try {
			setupDatabase();

			final IUser networkOwner = _orientDbGraph.getVertex(
					IdConverter.toRid(this.getLoggedInUser().getId()),
					IUser.class);

			checkForExistingNetwork(newNetwork, networkOwner);

			final Map<String, VertexFrame> networkIndex = Maps.newHashMap();

			final INetwork network = _orientDbGraph.addVertex("class:network",
					INetwork.class);
			network.setIsComplete(true);
			network.setIsLocked(false);
			network.setIsPublic(newNetwork.getIsPublic());
			network.setName(newNetwork.getName());
			network.setMetadata(newNetwork.getMetadata());
			network.setMetaterms(new HashMap<String, IBaseTerm>());
			network.setNdexEdgeCount(newNetwork.getEdges().size());
			network.setNdexNodeCount(newNetwork.getNodes().size());

			createNetworkMembers(newNetwork, networkOwner, network);

			// Namespaces must be created first since they can be referenced by
			// terms.
			createNamespaces(network, newNetwork, networkIndex);
			System.out.println(networkIndex.values().size() + " entries in map after Namespaces");


			// Terms must be created second since they reference other terms
			// and are also referenced by nodes/edges.
			createTerms(network, newNetwork, networkIndex);
			System.out.println(networkIndex.values().size() + " entries in map after Terms");


			// Citations must be created next as they're
			// referenced by supports and edges.	
			createCitations(network, newNetwork, networkIndex);
			System.out.println(networkIndex.values().size() + " entries in map after Citations");

			
			// Supports and Nodes must be created next as they're
			// referenced by edges.
			createSupports(network, newNetwork, networkIndex);
			System.out.println(networkIndex.values().size() + " entries in map after Supports");

			createNodes(network, newNetwork, networkIndex);
			System.out.println(networkIndex.values().size() + " entries in map after Nodes");

			
			// Finally, we create edges
			createEdges(network, newNetwork, networkIndex);
			System.out.println(networkIndex.values().size() + " entries in map after Edges");


			return new Network(network);

		} finally {
			teardownDatabase();
		}
	}

	private void createNetworkMembers(final Network newNetwork,
			final IUser networkOwner, final INetwork network) {
		if (newNetwork.getMembers() == null
				|| newNetwork.getMembers().size() == 0) {
			final INetworkMembership membership = _orientDbGraph.addVertex(
					"class:networkMembership", INetworkMembership.class);
			membership.setPermissions(Permissions.ADMIN);
			membership.setMember(networkOwner);
			membership.setNetwork(network);
		} else {
			for (final Membership member : newNetwork.getMembers()) {
				final IUser networkMember = _orientDbGraph.getVertex(
						IdConverter.toRid(member.getResourceId()), IUser.class);

				final INetworkMembership membership = _orientDbGraph.addVertex(
						"class:networkMembership", INetworkMembership.class);
				membership.setPermissions(member.getPermissions());
				membership.setMember(networkMember);
				membership.setNetwork(network);
			}
		}
	}

	private void checkForExistingNetwork(final Network newNetwork,
			final IUser networkOwner) throws DuplicateObjectException {
		final List<ODocument> existingNetworks = _ndexDatabase
				.query(new OSQLSynchQuery<Object>(
						"SELECT @RID FROM Network WHERE out_networkMemberships.in_accountNetworks.username = '"
								+ networkOwner.getUsername()
								+ "' AND name = '"
								+ newNetwork.getName() + "'"));
		if (!existingNetworks.isEmpty())
			throw new DuplicateObjectException(
					"You already have a network titled: "
							+ newNetwork.getName());
	}

	/**************************************************************************
	 * Creates a network.
	 * 
	 * @param ownerId
	 *            The ID of the user creating the group.
	 * @param sourceNetwork
	 *            The network to create.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws DuplicateObjectException
	 *             The user already has a network with the same title.
	 * @throws NdexException
	 *             Failed to create the network in the database.
	 * @return The newly created network.
	 **************************************************************************/
	/*
	 * refactored to support no-transactional database operations
	 */
	@PUT
	@Produces("application/json")
	@Path("/{networkId}/{equivalenceMethod}")
	@ApiDoc("Adds network content from a JDEx structure (source) to an existing network (target). Does not copy network metadata. "
			+ "Equivalence between network elements is determined by equivalenceMethod, elements with equivalents in target are not copied. "
			+ "Errors if the JDEx is not provided or if networkId is not found.")
	public Network addNetwork(@PathParam("networkId") final String networkId,
			@PathParam("equivalenceMethod") final String equivalenceMethod,
			final Network sourceNetwork) throws IllegalArgumentException,
			DuplicateObjectException, NdexException {
		Preconditions.checkArgument(null != sourceNetwork,
				"A source network structure is required");
		final ORID networkRid = IdConverter.toRid(networkId);

		try {
			setupDatabase();

			final INetwork targetNetwork = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (targetNetwork == null)
				throw new ObjectNotFoundException("Network", networkId);
			else if (!hasPermission(new Network(targetNetwork), Permissions.ADMIN)) 
				throw new SecurityException(
						"Insufficient privileges to add content to this network.");

			final Map<String, VertexFrame> networkIndex = Maps.newHashMap();

			final EquivalenceFinder equivalenceFinder = getEquivalenceFinder(
					equivalenceMethod, targetNetwork, networkIndex);

			// Namespaces not found in target must be created
			// first since they can be referenced by terms.
			createNamespaces(sourceNetwork, equivalenceFinder);
			
			System.out.println(networkIndex.values().size() + " entries in map after Namespaces");

			// Terms not found in target are then created.
			// They can reference other terms
			// and are also referenced by nodes and edges.
			createTerms(sourceNetwork, equivalenceFinder);
			
			System.out.println(networkIndex.values().size() + " entries in map after Terms");


			// Citations not found in target are then created
			// They can be referenced by supports and edges.
			createCitations(sourceNetwork, equivalenceFinder);
			
			System.out.println(networkIndex.values().size() + " entries in map after Citations");

			
			// Supports not found in target are then created
			// They can be referenced by edges.
			createSupports(sourceNetwork, equivalenceFinder);
			
			System.out.println(networkIndex.values().size() + " entries in map after Supports");


			// Nodes not found in target are then created
			// They can be referenced by edges.
			createNodes(sourceNetwork, equivalenceFinder);
			
			System.out.println(networkIndex.values().size() + " entries in map after Nodes");

			
			// Finally, edges not found in target are created
			createEdges(sourceNetwork, equivalenceFinder);
			
			System.out.println(networkIndex.values().size() + " entries in map after Edges");


			return new Network(targetNetwork);

		} finally {
			teardownDatabase();
		}
	}

	private EquivalenceFinder getEquivalenceFinder(String equivalenceMethod,
			INetwork target, Map<String, VertexFrame> networkIndex) {
		if ("JDEX_ID" == equivalenceMethod)
			return new IdEquivalenceFinder(target, networkIndex, _ndexDatabase,
					_orientDbGraph);
		throw new IllegalArgumentException("Unknown EquivalenceMethod: "
				+ equivalenceMethod);
	}

	/**************************************************************************
	 * Deletes a network.
	 * 
	 * @param networkId
	 *            The ID of the network to delete.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network doesn't exist.
	 * @throws NdexException
	 *             Failed to delete the network from the database.
	 **************************************************************************/
	@DELETE
	@Path("/{networkId}")
	@Produces("application/json")
	@ApiDoc("Deletes the network specified by networkId. Errors if the networkId is not provided, if the network is not found, if the network has other admin users, or if the authenticated user has insufficient privelges.")
	public void deleteNetwork(@PathParam("networkId") final String networkId)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId),
				"A network id is required");

		final ORID networkRid = IdConverter.toRid(networkId);

		try {
			setupDatabase();

			final INetwork networkToDelete = _orientDbGraph.getVertex(
					networkRid, INetwork.class);
			if (networkToDelete == null)
				throw new ObjectNotFoundException("Network", networkId);
			else if (!hasPermission(new Network(networkToDelete),
					Permissions.ADMIN))
				throw new SecurityException(
						"Insufficient privileges to delete the group.");

			final List<ODocument> adminCount = _ndexDatabase
					.query(new OSQLSynchQuery<Integer>(
							"SELECT COUNT(@RID) FROM Membership WHERE in_members = "
									+ networkRid + " AND permissions = 'ADMIN'"));
			if (adminCount == null || adminCount.isEmpty())
				throw new NdexException("Unable to count ADMIN members.");
			else if ((long) adminCount.get(0).field("COUNT") > 1)
				throw new NdexException(
						"Cannot delete a network that contains other ADMIN members.");

			for (INetworkMembership networkMembership : networkToDelete
					.getMembers())
				_orientDbGraph.removeVertex(networkMembership.asVertex());

			final List<ODocument> networkChildren = _ndexDatabase
					.query(new OSQLSynchQuery<Object>(
							"SELECT @RID FROM (TRAVERSE * FROM "
									+ networkRid
									+ " WHILE @class <> 'network' AND @class <> 'user' AND @class <> 'group')"));
			for (ODocument networkChild : networkChildren) {
				final OrientElement element = _orientDbGraph.getBaseGraph()
						.getElement(networkChild.field("rid", OType.LINK));
				if (element != null)
					element.remove();
			}

			_orientDbGraph.removeVertex(networkToDelete.asVertex());
			_orientDbGraph.getBaseGraph().commit();
		} catch (SecurityException | NdexException ne) {
			throw ne;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1) {
				throw new ObjectNotFoundException("Network", networkId);
			}
			throw e;
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Searches for a network.
	 * 
	 * @param searchParameters
	 *            The search parameters.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return Networks that match the search parameters.
	 **************************************************************************/
	@POST
	@PermitAll
	@Path("/search/{searchOperator}")
	@Produces("application/json")
	@ApiDoc("Returns a list of networks based on the searchOperator and the POSTed searchParameters.")
	public List<Network> findNetworks(final SearchParameters searchParameters,
			@PathParam("searchOperator") final String searchOperator)
			throws IllegalArgumentException, NdexException {
		if (searchParameters == null)
			throw new IllegalArgumentException("Search Parameters are empty.");
		else if (searchParameters.getSearchString() == null
				|| searchParameters.getSearchString().isEmpty())
			throw new IllegalArgumentException(
					"No search string was specified.");
		else
			searchParameters.setSearchString(searchParameters.getSearchString()
					.trim());

		final List<Network> foundNetworks = new ArrayList<Network>();
		final String query = buildSearchQuery(searchParameters, searchOperator);

		try {
			setupDatabase();

			final List<ODocument> networks = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(query));
			for (final ODocument network : networks)
				foundNetworks.add(new Network(_orientDbGraph.getVertex(network,
						INetwork.class)));

			return foundNetworks;
		} catch (Exception e) {
			_logger.error("Failed to search networks.", e);
			throw new NdexException("Failed to search networks.");
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets a network by ID.
	 * 
	 * @param networkId
	 *            The ID of the network.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws SecurityException
	 *             The user doesn't have access to the network.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The network.
	 **************************************************************************/
	@GET
	@Path("/{networkId}")
	@Produces("application/json")
	@ApiDoc("Returns a network structure populated with metadata and membership information for the network specified by networkId. Errors if the network is not found or if the authenticated user does not have read permission for the network.")
	public Network getNetwork(@PathParam("networkId") final String networkId)
			throws IllegalArgumentException, SecurityException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(
					IdConverter.toRid(networkId), INetwork.class);
			if (network == null)
				return null;
			else if (!network.getIsPublic()) {
				for (Membership userMembership : this.getLoggedInUser()
						.getNetworks()) {
					if (userMembership.getResourceId().equals(networkId))
						return new Network(network);
				}

				throw new SecurityException(
						"You do not have access to that network.");
			} else
				return new Network(network);
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets a page of edges for the specified network, along with the supporting
	 * nodes, terms, namespaces, supports, and citations.
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param skip
	 *            The number of edges to skip.
	 * @param top
	 *            The number of edges to retrieve.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The edges of the network.
	 **************************************************************************/
	@GET
	@Path("/{networkId}/edges/{skip}/{top}")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a set of edges selected from the network specified by networkId. The returned network is fully poplulated and 'self-sufficient', including all nodes, terms, supports, citations, and namespaces. The query selects a number of edges specified by the 'top' parameter, starting at an offset specified by the 'skip' parameter.")
	public Network getEdges(@PathParam("networkId") final String networkId,
			@PathParam("skip") final int skip, @PathParam("top") final int top)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");
		else if (top < 1)
			throw new IllegalArgumentException(
					"Number of results to return is less than 1.");

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(
					IdConverter.toRid(networkId), INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

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
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}

	
	/**************************************************************************
	 * Gets a subnetwork of a network corresponding to a page of edges for a specified 
	 * set of citations in the network.
	 * 
	 * POST DATA: citations - list of strings
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param skip
	 *            The number of edges to skip.
	 * @param top
	 *            The number of edges to retrieve.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The edges of the network.
	 **************************************************************************/
	@POST
	@Path("/{networkId}/citations/edges/{skip}/{top}")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a set of edges selected from the network specified by networkId and linked to the citations specified by the POSTed set of citation ids. "
			+ "The returned network is fully poplulated and 'self-sufficient', including all nodes, terms, supports, citations, and namespaces. "
			+ "The query traverses from the specified citations to find edges, then selects a number of edges specified by the 'top' parameter, "
			+ "starting at an offset specified by the 'skip' parameter.")
	public Network getEdgesByCitations(@PathParam("networkId") final String networkId,
			@PathParam("skip") final int skip, @PathParam("top") final int top, 
			final String[] citations)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");
		if (citations == null || citations.length < 1)
			throw new IllegalArgumentException("No citation IDs were specified.");
		else if (top < 1)
			throw new IllegalArgumentException(
					"Number of edges to find is less than 1.");

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(
					IdConverter.toRid(networkId), INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);
			
			// Check that all citations are elements of the network
			final String citationIdCsv = IdConverter.toRidCsv(citations);
			final String citationQuery = "SELECT FROM (TRAVERSE out_networkCitations from " + network.asVertex().getId()
					+ " ) WHERE @RID in [ " + citationIdCsv + " ]";
			final List<ODocument> citationsFound = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(citationQuery));
			if (null == citationsFound || citationsFound.size() != citations.length)
				throw new ObjectNotFoundException("One or more citations with ids in [" + citationIdCsv + "] was not found in network " + networkId);

			final List<IEdge> foundIEdges = new ArrayList<IEdge>();
			final int startIndex = skip * top;
			
			
			// Find edges from the citations
			final String edgeQuery = "SELECT FROM (TRAVERSE in_edgeCitations, out_citationSupports, in_edgeSupports from [ " 
					+ citationIdCsv
					+ " ]) WHERE @class = 'edge' SKIP " + startIndex + " LIMIT " + top;
			
			final List<ODocument> edgesFound = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(edgeQuery));
			
			for (final ODocument edge : edgesFound) {
				foundIEdges.add(_orientDbGraph.getVertex(edge, IEdge.class));
			}
			
			return getNetworkBasedOnFoundEdges(foundIEdges, network);
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to query network by citations : " + networkId + ".", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets all BaseTerms in the network that are in Namespaces identified by a
	 * list of Namespace prefixes
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param namespaces
	 *            A list of namespace prefixes, i.e. HGNC, UniProt, etc.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The BaseTerms in the found the Namespaces
	 **************************************************************************/
	@POST
	@Path("/{networkId}/namespaces")
	@Produces("application/json")
	@ApiDoc("Returns a list of all base terms in the network that are in namespaces identified by the POSTed list of namespace prefixes.")
	public List<BaseTerm> getTermsInNamespaces(
			@PathParam("networkId") final String networkId,
			final String namespaces[]) throws IllegalArgumentException,
			NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");

		final List<BaseTerm> foundBaseTerms = new ArrayList<BaseTerm>();

		String joinedNamespaces = "";
		for (final String namespace : namespaces)
			joinedNamespaces += "'" + namespace + "',";

		joinedNamespaces = joinedNamespaces.substring(0,
				joinedNamespaces.length() - 1);

		final ORID networkRid = IdConverter.toRid(networkId);
		final String query = "SELECT FROM Namespace\n"
				+ "WHERE in_networkNamespaces = " + networkRid + "\n"
				+ "  AND prefix IN [" + joinedNamespaces + "]\n"
				+ "ORDER BY prefix";

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

			final List<ODocument> namespacesFound = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(query));
			for (final ODocument namespace : namespacesFound) {
				ORID namespaceId = namespace.getIdentity();
				final String termQuery = "SElECT FROM ( TRAVERSE in_baseTermNamespace from "
						+ namespaceId + " ) WHERE termType = 'Base' ";
				// INamespace iNamespace = _orientDbGraph.getVertex(namespace,
				// INamespace.class);
				// Namespace ns = new Namespace(iNamespace);
				final List<ODocument> baseTermsFound = _ndexDatabase
						.query(new OSQLSynchQuery<ODocument>(termQuery));
				for (final ODocument baseTerm : baseTermsFound) {
					final IBaseTerm iBaseTerm = _orientDbGraph.getVertex(
							baseTerm, IBaseTerm.class);
					final BaseTerm bt = new BaseTerm(iBaseTerm);
					foundBaseTerms.add(bt);
				}
			}
			return foundBaseTerms;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets all terms in the network that intersect with a list of terms.
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param terms
	 *            A list of terms being sought.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The edges of the network.
	 **************************************************************************/
	@POST
	@Path("/{networkId}/terms")
	@Produces("application/json")
	@ApiDoc("Returns a list of all base terms in the network specified by networkId that are in the POSTed list of terms.")
	public Iterable<BaseTerm> getIntersectingTerms(
			@PathParam("networkId") final String networkId,
			final String terms[]) throws IllegalArgumentException,
			NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");

		final List<BaseTerm> foundTerms = new ArrayList<BaseTerm>();

		String joinedTerms = "";
		for (final String baseTerm : terms)
			joinedTerms += "'" + baseTerm + "',";

		joinedTerms = joinedTerms.substring(0, joinedTerms.length() - 2);

		final ORID networkRid = IdConverter.toRid(networkId);
		final String query = "SELECT FROM BaseTerm\n"
				+ "WHERE in_networkTerms = " + networkRid + "\n"
				+ "  AND name IN [" + joinedTerms + "]\n" + "ORDER BY name";

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

			final List<ODocument> baseTerms = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(query));
			for (final ODocument baseTerm : baseTerms)
				foundTerms.add(new BaseTerm(_orientDbGraph.getVertex(baseTerm,
						IBaseTerm.class)));

			return foundTerms;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets a page of namespaces for the specified network.
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param skip
	 *            The number of namespaces to skip.
	 * @param top
	 *            The number of namespaces to retrieve.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The edges of the network.
	 **************************************************************************/
	@GET
	@Path("/{networkId}/namespaces/{skip}/{top}")
	@Produces("application/json")
	@ApiDoc("Returns a list of namespaces in the network specified by networkId. 'top' specified the number of namespaces to retrieve, 'skip' specifies the number to skip.")
	public Iterable<Namespace> getNamespaces(
			@PathParam("networkId") final String networkId,
			@PathParam("skip") final int skip, @PathParam("top") final int top)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");
		else if (top < 1)
			throw new IllegalArgumentException(
					"Number of results to return is less than 1.");

		final List<Namespace> foundNamespaces = new ArrayList<Namespace>();

		final int startIndex = skip * top;
		final ORID networkRid = IdConverter.toRid(networkId);
		final String query = "SELECT FROM Namespace\n"
				+ "WHERE in_networkNamespaces = " + networkRid + "\n"
				+ "ORDER BY prefix\n" + "SKIP " + startIndex + "\n" + "LIMIT "
				+ top;

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

			final List<ODocument> namespaces = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(query));
			for (final ODocument namespace : namespaces)
				foundNamespaces.add(new Namespace(_orientDbGraph.getVertex(
						namespace, INamespace.class)));

			return foundNamespaces;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets a page of terms for the specified network.
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param skip
	 *            The number of terms to skip.
	 * @param top
	 *            The number of terms to retrieve.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The edges of the network.
	 **************************************************************************/
	@GET
	@Path("/{networkId}/terms/{skip}/{top}")
	@Produces("application/json")
	@ApiDoc("Returns a list of terms in the network specified by networkId. 'top' specified the number of terms to retrieve, 'skip' specifies the number to skip.")
	public Iterable<BaseTerm> getTerms(
			@PathParam("networkId") final String networkId,
			@PathParam("skip") final int skip, @PathParam("top") final int top)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");
		else if (top < 1)
			throw new IllegalArgumentException(
					"Number of results to return is less than 1.");

		final List<BaseTerm> foundTerms = new ArrayList<BaseTerm>();

		final int startIndex = skip * top;
		final ORID networkRid = IdConverter.toRid(networkId);
		final String query = "SELECT FROM BaseTerm\n"
				+ "WHERE in_networkTerms = " + networkRid + "\n"
				+ "ORDER BY name\n" + "SKIP " + startIndex + "\n" + "LIMIT "
				+ top;

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

			final List<ODocument> baseTerms = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(query));
			for (final ODocument baseTerm : baseTerms)
				foundTerms.add(new BaseTerm(_orientDbGraph.getVertex(baseTerm,
						IBaseTerm.class)));

			return foundTerms;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}
	
	/**************************************************************************
	 * Gets a page of citations for the specified network.
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param skip
	 *            The number of terms to skip.
	 * @param top
	 *            The number of terms to retrieve.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return The an iterable of Citation objects
	 **************************************************************************/
	@GET
	@Path("/{networkId}/citations/{skip}/{top}")
	@Produces("application/json")
	@ApiDoc("Returns a list of citations in the network specified by networkId. 'top' specified the number of citations to retrieve in each block, 'skip' specifies the number of blocks to skip.")
	public List<Citation> getCitations(
			@PathParam("networkId") final String networkId,
			@PathParam("skip") final int skip, @PathParam("top") final int top)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");
		else if (top < 1)
			throw new IllegalArgumentException(
					"Number of results to return is less than 1.");

		final List<Citation> foundCitations = new ArrayList<Citation>();

		final int startIndex = skip * top;
		final ORID networkRid = IdConverter.toRid(networkId);
		final String citationQuery = "SELECT FROM (TRAVERSE out_networkCitations from " + networkRid
				+ " while $depth < 2) WHERE @class = 'citation' SKIP " + startIndex + "\n" + "LIMIT "
				+ top;

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);
			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);

			final List<ODocument> citations = _ndexDatabase
					.query(new OSQLSynchQuery<ODocument>(citationQuery));
			for (final ODocument citation : citations)
				foundCitations.add(new Citation(_orientDbGraph.getVertex(citation,
						ICitation.class)));

			return foundCitations;
		} catch (ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException(e.getMessage());
		} finally {
			teardownDatabase();
		}
	}


	/**************************************************************************
	 * Gets a subnetwork of a network based on network query parameters.
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param queryParameters
	 *            The query parameters.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return A subnetwork of the network.
	 **************************************************************************/
	@POST
	@Path("/{networkId}/query1")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a set of edges selected based on the POSTed queryParameters from the network specified by networkId. The returned network is fully poplulated and 'self-sufficient', including all nodes, terms, supports, citations, and namespaces.")
	public Network queryNetwork(@PathParam("networkId") final String networkId,
			final NetworkQueryParameters queryParameters)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(
					IdConverter.toRid(networkId), INetwork.class);
			if (network == null)
				return null;
			else {
				List<Term> baseTerms = getBaseTermsByName(network,
						queryParameters.getStartingTermStrings().get(0));
				if (!baseTerms.isEmpty()) {
					queryParameters.addStartingTermId(baseTerms.get(0).getId());

					List<IEdge> foundIEdges = neighborhoodQuery(network,
							queryParameters);
					return getNetworkBasedOnFoundEdges(foundIEdges, network);
				} else
					return null;
			}
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException("Failed to query the network.");
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Gets a subnetwork network based on network query parameters.
	 * 
	 * @param networkId
	 *            The network ID.
	 * @param queryParameters
	 *            The query parameters.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to query the database.
	 * @return A subnetwork of the network.
	 **************************************************************************/
	@POST
	@Path("/{networkId}/query")
	@Produces("application/json")
	@ApiDoc("Returns a network based on a set of edges selected based on the POSTed queryParameters from the network specified by networkId. The returned network is fully poplulated and 'self-sufficient', including all nodes, terms, supports, citations, and namespaces.")
	public Network queryNetwork2(
			@PathParam("networkId") final String networkId,
			final NetworkQueryParameters queryParameters)
			throws IllegalArgumentException, NdexException {
		if (networkId == null || networkId.isEmpty())
			throw new IllegalArgumentException("No network ID was specified.");

		try {
			setupDatabase();

			final INetwork network = _orientDbGraph.getVertex(
					IdConverter.toRid(networkId), INetwork.class);
			if (network == null)
				return null;
			else {
				List<IBaseTerm> startingTerms = getBaseTermsByNames(network,
						queryParameters.getStartingTermStrings());

				if (!startingTerms.isEmpty()) {
					List<INode> startingNodes = getNodesFromTerms(
							startingTerms, true);
					if (!startingNodes.isEmpty()) {
						List<IEdge> foundIEdges = neighborhoodNodeQuery(
								network, startingNodes,
								queryParameters.getSearchDepth(),
								queryParameters.getSearchType());

						/*
						 * List<IEdge> foundIEdges = neighborhoodQuery2(
						 * network, startingNodes,
						 * queryParameters.getSearchDepth(),
						 * queryParameters.getSearchType(), 1000 );
						 */
						return getNetworkBasedOnFoundEdges(foundIEdges, network);
					}
				}
			}
			return null;
		} catch (Exception e) {
			_logger.error("Failed to query network: " + networkId + ".", e);
			throw new NdexException("Failed to query the network.");
		} finally {
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
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network or member doesn't exist.
	 * @throws SecurityException
	 *             The user doesn't have access to change members.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	@DELETE
	@Path("/{networkId}/member/{userId}")
	@Produces("application/json")
	@ApiDoc("Removes a member specified by userId from the network specified by networkId. Errors if the authenticated user does not have sufficient permissions or if the network or user is not found. Removal is also denied if it would leave the network without any Admin member.")
	public void removeMember(@PathParam("networkId") final String networkId,
			@PathParam("userId") final String userId)
			throws IllegalArgumentException, ObjectNotFoundException,
			SecurityException, NdexException {

		Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId),
				"A network id is required");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(userId),
				"A user id is required");

		try {
			setupDatabase();

			final ORID networkRid = IdConverter.toRid(networkId);
			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);

			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);
			else if (!hasPermission(new Network(network), Permissions.ADMIN))
				throw new SecurityException("Access denied.");

			final IUser user = _orientDbGraph.getVertex(
					IdConverter.toRid(userId), IUser.class);
			if (user == null)
				throw new ObjectNotFoundException("User", userId);

			for (INetworkMembership networkMember : network.getMembers()) {
				String memberId = IdConverter.toJid((ORID) networkMember
						.getMember().asVertex().getId());
				if (memberId.equals(userId)) {
					if (countAdminMembers(networkRid) < 2)
						throw new SecurityException(
								"Cannot remove the only ADMIN member.");

					network.removeMember(networkMember);
					user.removeNetwork(networkMember);
					_orientDbGraph.getBaseGraph().commit();
					return;
				}
			}
		} catch (ObjectNotFoundException | SecurityException ne) {
			throw ne;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1) {
				throw new ObjectNotFoundException("Network", networkId);
			}

			_logger.error("Failed to remove member.", e);

			throw new NdexException("Failed to remove member.");
		} finally {
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
	 *             Bad input.
	 * @throws ObjectNotFoundException
	 *             The network or member doesn't exist.
	 * @throws SecurityException
	 *             The user doesn't have access to change members.
	 * @throws NdexException
	 *             Failed to query the database.
	 **************************************************************************/
	@POST
	@Path("/{networkId}/member")
	@Produces("application/json")
	@ApiDoc("Updates the permission of a member specified by userId for the network specified by networkId to the POSTed permission. Errors if the authenticated user does not have sufficient permissions or if the network or user is not found. Change is also denied if it would leave the network without any Admin member.")
	public void updateMember(@PathParam("networkId") final String networkId,
			final Membership networkMember) throws IllegalArgumentException,
			ObjectNotFoundException, SecurityException, NdexException {

		try {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(networkId),
					"A network id is required");
			Preconditions.checkNotNull(networkMember,
					"A network member is required");
			Preconditions.checkState(
					!Strings.isNullOrEmpty(networkMember.getResourceId()),
					"The network member must have a resource id");
		} catch (Exception e1) {
			throw new IllegalArgumentException(e1);
		}

		try {
			setupDatabase();

			final ORID networkRid = IdConverter.toRid(networkId);
			final INetwork network = _orientDbGraph.getVertex(networkRid,
					INetwork.class);

			if (network == null)
				throw new ObjectNotFoundException("Network", networkId);
			else if (!hasPermission(new Network(network), Permissions.ADMIN))
				throw new SecurityException("Access denied.");

			final IUser user = _orientDbGraph.getVertex(
					IdConverter.toRid(networkMember.getResourceId()),
					IUser.class);
			if (user == null)
				throw new ObjectNotFoundException("User",
						networkMember.getResourceId());

			for (INetworkMembership networkMembership : network.getMembers()) {
				String memberId = IdConverter.toJid((ORID) networkMembership
						.getMember().asVertex().getId());
				if (memberId.equals(networkMember.getResourceId())) {
					if (countAdminMembers(networkRid) < 2)
						throw new SecurityException(
								"Cannot change the permissions on the only ADMIN member.");

					networkMembership.setPermissions(networkMember
							.getPermissions());
					_orientDbGraph.getBaseGraph().commit();
					return;
				}
			}
		} catch (ObjectNotFoundException | SecurityException ne) {
			throw ne;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1)
				throw new ObjectNotFoundException("Network", networkId);

			_logger.error(
					"Failed to update member: "
							+ networkMember.getResourceName() + ".", e);

			throw new NdexException("Failed to update member: "
					+ networkMember.getResourceName() + ".");
		} finally {
			teardownDatabase();
		}
	}

	/**************************************************************************
	 * Updates a network.
	 * 
	 * @param updatedNetwork
	 *            The updated network information.
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws SecurityException
	 *             The user doesn't have permissions to update the network.
	 * @throws NdexException
	 *             Failed to update the network in the database.
	 **************************************************************************/
	@POST
	@Produces("application/json")
	@ApiDoc("Updates the metadata for the network specified by networkId based on the POSTed JDEx structure. "
			+ "Errors if the authenticated user does not have sufficient permissions or if the network is not found. ")
	public void updateNetwork(final Network updatedNetwork)
			throws IllegalArgumentException, SecurityException, NdexException {

		Preconditions.checkNotNull(updatedNetwork, "A Network is required");

		try {
			setupDatabase();

			final INetwork networkToUpdate = _orientDbGraph.getVertex(
					IdConverter.toRid(updatedNetwork.getId()), INetwork.class);
			if (networkToUpdate == null)
				throw new ObjectNotFoundException("Network",
						updatedNetwork.getId());
			else if (!hasPermission(updatedNetwork, Permissions.WRITE))
				throw new SecurityException("Access denied.");

			if (updatedNetwork.getDescription() != null
					&& !updatedNetwork.getDescription().equals(
							networkToUpdate.getDescription()))
				networkToUpdate.setDescription(updatedNetwork.getDescription());

			if (updatedNetwork.getIsLocked() != networkToUpdate.getIsLocked())
				networkToUpdate.setIsLocked(updatedNetwork.getIsLocked());

			if (updatedNetwork.getIsPublic() != networkToUpdate.getIsPublic())
				networkToUpdate.setIsPublic(updatedNetwork.getIsPublic());

			if (updatedNetwork.getName() != null
					&& !updatedNetwork.getName().equals(
							networkToUpdate.getName()))
				networkToUpdate.setName(updatedNetwork.getName());

			if (updatedNetwork.getMetadata() != null
					&& !updatedNetwork.getMetadata().equals(
							networkToUpdate.getMetadata()))
				networkToUpdate.setMetadata(updatedNetwork.getMetadata());

		} catch (SecurityException | ObjectNotFoundException onfe) {
			throw onfe;
		} catch (Exception e) {
			if (e.getMessage().indexOf("cluster: null") > -1)
				throw new ObjectNotFoundException("Network",
						updatedNetwork.getId());

			_logger.error(
					"Failed to update network: " + updatedNetwork.getName()
							+ ".", e);

			throw new NdexException("Failed to update the network.");
		} finally {
			teardownDatabase();
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
	 **************************************************************************/
	/*
	 * refactored to support non-transactional database operations
	 */
	@POST
	@Path("/upload")
	@Consumes("multipart/form-data")
	@Produces("application/json")
	@ApiDoc("Saves an uploaded file to a temporary directory and creates a task that specifies the file for parsing and import into the database. "
			+ "A background process running on the NDEx server processes file import tasks. "
			+ "Errors if the network is missing or if it has no filename or no file data.")
	public void uploadNetwork(@MultipartForm UploadedFile uploadedNetwork)
			throws IllegalArgumentException, SecurityException, NdexException {

		try {
			Preconditions
					.checkNotNull(uploadedNetwork, "A network is required");
			Preconditions.checkState(
					!Strings.isNullOrEmpty(uploadedNetwork.getFilename()),
					"A file name containg the network data is required");
			Preconditions.checkNotNull(uploadedNetwork.getFileData(),
					"Network file data is required");
			Preconditions.checkState(uploadedNetwork.getFileData().length > 0,
					"The file data is empty");
		} catch (Exception e1) {
			throw new IllegalArgumentException(e1);
		}

		final File uploadedNetworkPath = new File(Configuration.getInstance()
				.getProperty("Uploaded-Networks-Path"));
		if (!uploadedNetworkPath.exists())
			uploadedNetworkPath.mkdir();

		final File uploadedNetworkFile = new File(
				uploadedNetworkPath.getAbsolutePath() + "/"
						+ uploadedNetwork.getFilename());

		try {
			if (!uploadedNetworkFile.exists())
				uploadedNetworkFile.createNewFile();

			final FileOutputStream saveNetworkFile = new FileOutputStream(
					uploadedNetworkFile);
			saveNetworkFile.write(uploadedNetwork.getFileData());
			saveNetworkFile.flush();
			saveNetworkFile.close();

			setupDatabase();

			final IUser taskOwner = _orientDbGraph.getVertex(
					IdConverter.toRid(this.getLoggedInUser().getId()),
					IUser.class);

			final String fn = uploadedNetwork.getFilename().toLowerCase();

			if (fn.endsWith(".sif") || fn.endsWith(".xbel")
					|| fn.endsWith(".xgmml") || fn.endsWith(".xls")
					|| fn.endsWith(".xlsx")) {
				ITask processNetworkTask = _orientDbGraph.addVertex(
						"class:task", ITask.class);
				processNetworkTask.setDescription("Process uploaded network");
				processNetworkTask.setType(TaskType.PROCESS_UPLOADED_NETWORK);
				processNetworkTask.setOwner(taskOwner);
				processNetworkTask.setPriority(Priority.LOW);
				processNetworkTask.setProgress(0);
				processNetworkTask.setResource(uploadedNetworkFile
						.getAbsolutePath());
				processNetworkTask.setStartTime(new Date());
				processNetworkTask.setStatus(Status.QUEUED);

				_orientDbGraph.getBaseGraph().commit();
			} else {
				uploadedNetworkFile.delete();
				throw new IllegalArgumentException(
						"The uploaded file type is not supported; must be Excel, XGMML, SIF, OR XBEL.");
			}
		} catch (IllegalArgumentException iae) {
			throw iae;
		} catch (Exception e) {
			_logger.error("Failed to process uploaded network: "
					+ uploadedNetwork.getFilename() + ".", e);

			throw new NdexException(e.getMessage());
		}
	}

	private List<INode> getNodesFromTerms(List<IBaseTerm> baseTerms,
			boolean includeAliases) {
		List<INode> result = new ArrayList<INode>();
		String termIdCsv = joinBaseTermIdsToCsv(baseTerms);

		// Example query tested:
		// select @RID from (traverse in_functionTermParameters,
		// in_nodeRepresents from #15:277536, #15:273306 while $depth < 4) where
		// @CLASS='node' limit 10

		String traverseEdgeTypes = null;
		if (includeAliases) {
			traverseEdgeTypes = "in_functionTermParameters, in_nodeRepresents, in_nodeRelationshipAliases, in_nodeUnificationAliases";
		} else {
			traverseEdgeTypes = "in_functionTermParameters, in_nodeRepresents";
		}

		final String query = "SELECT FROM (traverse " + traverseEdgeTypes
				+ " from \n[" + termIdCsv + "] \n" + "WHILE $depth < 10) \n"
				+ "WHERE @CLASS='node' ";
		// System.out.println("node query: " + query);
		final List<ODocument> nodes = _ndexDatabase
				.query(new OSQLSynchQuery<ODocument>(query));
		for (final ODocument node : nodes)
			result.add(_orientDbGraph.getVertex(node, INode.class));
		return result;
	}

	private List<IBaseTerm> getBaseTermsByNames(INetwork network,
			List<String> baseTermNames) throws NdexException {
		final List<IBaseTerm> foundTerms = new ArrayList<IBaseTerm>();
		String termNameCsv = joinStringsToCsv(baseTermNames);

		// select from (traverse out_networkTerms from #24:733
		// while $depth < 2) where @CLASS='baseTerm' and name like "AKT%" limit
		// 10
		final String query = "SELECT FROM (traverse out_networkTerms from "
				+ network.asVertex().getId() + " \n" + "WHILE $depth < 2) \n"
				+ "WHERE @CLASS='baseTerm' AND name IN [" + termNameCsv + "] ";

		final List<ODocument> baseTerms = _ndexDatabase
				.query(new OSQLSynchQuery<ODocument>(query));
		for (final ODocument baseTerm : baseTerms)
			foundTerms.add(_orientDbGraph.getVertex(baseTerm, IBaseTerm.class));

		return foundTerms;
	}

	/*
	 * private List<IEdge> neighborhoodQuery2(INetwork network, List<INode>
	 * startingNodes, int searchDepth, String searchType, int i) { final
	 * List<IEdge> foundEdges = new ArrayList<IEdge>(); String nodeIdCsv =
	 * joinNodeIdsToCsv(startingNodes); // select out_edgePredicate.name from
	 * (traverse in_edgeSubject, in_edgeObject from #27:22987 while $depth < 6)
	 * where @CLASS = 'edge' limit 100 ifinal String query =
	 * "SELECT FROM (traverse out_networkTerms from " +
	 * network.asVertex().getId() + " \n" + "WHILE $depth < 2) \n" +
	 * "WHERE @CLASS='baseTerm' AND name IN [" + termNameCsv + "] ";
	 * 
	 * 
	 * final List<ODocument> edges = _ndexDatabase .query(new
	 * OSQLSynchQuery<ODocument>(query)); for (final ODocument edge : edges)
	 * foundEdges.add(_orientDbGraph.getVertex(edge, IEdge.class)); return
	 * foundEdges; }
	 */

	private static void addTermAndFunctionalDependencies(final ITerm term,
			final Set<ITerm> terms) {
		if (terms.add(term)) {
			if (term instanceof IFunctionTerm) {
				terms.add(((IFunctionTerm) term).getTermFunc());

				for (ITerm iterm : ((IFunctionTerm) term).getTermParameters()) {
					addTermAndFunctionalDependencies(iterm, terms);
				}
			}
		}
	}

	/**************************************************************************
	 * Builds the SQL for the query to find networks. Note: just because a query
	 * works in the OrientDB console, doesn't mean it'll work in the Java query
	 * parser. The parsers are different or the Java parser has bugs as of
	 * January 2014.
	 * 
	 * Key bug found: You must always put a space after ending parentheses. *
	 * (Unless it is the last char in the whole query)
	 * 
	 * @param searchParameters
	 *            The search parameters.
	 * @param searchOperator
	 *            The operator used for the default search.
	 * @return A string containing the SQL query.
	 **************************************************************************/
	private String buildSearchQuery(final SearchParameters searchParameters,
			final String searchOperator) {
		String query = "";

		// CASE: BaseTerm Search
		// Some test queries. Better to find terms by name then filter by
		// namespace and then get networks from namespaces...
		// GOOD: select in_networkTerms from (traverse in_baseTermNamespace from
		// (select from namespace where prefix = 'HGNC')) where name = 'CALM1'
		// BETTER: select in_networkNamespaces.name from (traverse
		// out_baseTermNamespace from (select from baseTerm where name in
		// ['CALM1'])) where prefix in ['HGNC'] limit 100
		if (searchParameters.getSearchString().contains("terms:")) {
			// query += parseTermParameters(searchParameters);
			query = "SELECT FROM "
					+ getTermSearchExpression(searchParameters
							.getSearchString());

			if (this.getLoggedInUser() != null) {
				query += "WHERE isComplete = true\n"
						+ "  AND (isPublic = true"
						+ " OR out_networkMemberships.in_accountNetworks.username = '"
						+ this.getLoggedInUser().getUsername() + "') \n";
			} else
				query += "WHERE isComplete = true AND isPublic = true\n";

		} else {
			// CASE: Name and Description Text Search
			// With option: Metadata Parameter Search
			// Replace all multiple spaces (left by previous parsing) with a
			// single
			// space

			final Pattern metadataRegex = Pattern
					.compile("\\[(.+)\\]([:~=])(\".+\")");
			final ArrayList<MetaParameter> metadataParameters = parseMetaParameters(
					searchParameters, metadataRegex);

			final Pattern metatermRegex = Pattern
					.compile("\\<(.+)\\>([:~=])(\".+\")");
			final ArrayList<MetaParameter> metatermParameters = parseMetaParameters(
					searchParameters, metatermRegex);

			searchParameters.setSearchString(searchParameters.getSearchString()
					.replace("  ", " ").toLowerCase().trim());

			query = "SELECT FROM Network\n";

			if (this.getLoggedInUser() != null) {
				query += "WHERE isComplete = true\n"
						+ "  AND (isPublic = true"
						+ " OR out_networkMemberships.in_accountNetworks.username = '"
						+ this.getLoggedInUser().getUsername() + "') \n";
			} else
				query += "WHERE isComplete = true AND isPublic = true\n";

			if (searchParameters.getSearchString().contains("-desc")) {
				searchParameters.getSearchString().replace(" -desc", "");
				query += "  AND name.toLowerCase() LIKE '"
						+ searchParameters.getSearchString() + "%' \n";
			}

			if (searchParameters.getSearchString() != null
					&& !searchParameters.getSearchString().isEmpty()) {
				if (searchOperator.equals("exact-match")) {
					query += "  AND (name.toLowerCase() = '"
							+ searchParameters.getSearchString() + "'"
							+ " OR description.toLowerCase() = '"
							+ searchParameters.getSearchString() + "') \n";
				} else if (searchOperator.equals("contains")) {
					query += "  AND (name.toLowerCase() LIKE '%"
							+ searchParameters.getSearchString() + "%'"
							+ " OR description.toLowerCase() LIKE '%"
							+ searchParameters.getSearchString() + "%') \n";
				} else {
					query += "  AND (name.toLowerCase() LIKE '"
							+ searchParameters.getSearchString() + "%'"
							+ " OR description.toLowerCase() LIKE '"
							+ searchParameters.getSearchString() + "%') \n";
				}
			}

			for (final MetaParameter metadataParameter : metadataParameters)
				query += "  AND metadata['" + metadataParameter.getKey() + "']"
						+ metadataParameter.toString() + " \n";

			for (final MetaParameter metatermParameter : metatermParameters)
				query += "  AND metadata['" + metatermParameter.getKey() + "']"
						+ metatermParameter.toString() + " \n";

			final int startIndex = searchParameters.getSkip()
					* searchParameters.getTop();
			query += "ORDER BY name DESC\n" + "SKIP " + startIndex + "\n"
					+ "LIMIT " + searchParameters.getTop();

		}

		_logger.debug(query);
		return query;
	}

	private String getTermSearchExpression(final String searchString) {
		final Pattern termRegex = Pattern.compile("terms:\\{(.+)\\}");
		final Matcher termMatcher = termRegex.matcher(searchString);
		Set<String> termNameStrings = new HashSet<String>();
		Set<String> namespacePrefixStrings = new HashSet<String>();

		if (termMatcher.find()) {
			final String terms[] = termMatcher.group(1).split(",");

			for (final String term : terms) {
				final String namespaceAndTerm[] = term.split(":");
				if (namespaceAndTerm.length != 2)
					throw new IllegalArgumentException(
							"Error parsing terms from: \""
									+ termMatcher.group(0) + "\".");
				termNameStrings.add(namespaceAndTerm[1]);
				namespacePrefixStrings.add(namespaceAndTerm[0]);
			}
			String termNames = joinStringsToCsv(termNameStrings);
			String namespacePrefixes = joinStringsToCsv(namespacePrefixStrings);
			// SELECT @class, @rid, name FROM (TRAVERSE out_baseTermNamespace,
			// in_networkNamespaces FROM (SELECT FROM baseTerm where name in
			// ['CALM1']) WHILE $depth < 3 or ($depth = 1 and prefix IN
			// ['HGNC'])) where @class = 'network' limit 500
			final String searchExpression = " (TRAVERSE out_baseTermNamespace, in_networkNamespaces FROM "
					+ "(SELECT FROM baseTerm where name in ["
					+ termNames
					+ "]) "
					+ "WHILE $depth < 3 or ($depth = 1 and prefix IN ["
					+ namespacePrefixes + "])) ";

			return searchExpression;

		}

		return null;
	}

	/**************************************************************************
	 * Count the number of administrative members in the network.
	 **************************************************************************/
	private long countAdminMembers(final ORID networkRid) throws NdexException {
		final List<ODocument> adminCount = _ndexDatabase
				.query(new OSQLSynchQuery<Integer>(
						"SELECT COUNT(@RID) FROM NetworkMembership WHERE in_userNetworks = "
								+ networkRid + " AND permissions = 'ADMIN'"));
		if (adminCount == null || adminCount.isEmpty())
			throw new NdexException("Unable to count ADMIN members.");

		return (long) adminCount.get(0).field("COUNT");
	}

	/**************************************************************************
	 * NAMESPACES
	 * 
	 * Map namespaces in network model object to namespaces in the network domain object
	 * 
	 **************************************************************************/
	private void createNamespaces(final INetwork newNetwork,
			final Network networkToCreate,
			final Map<String, VertexFrame> networkIndex) {
		for (final Entry<String, Namespace> namespaceEntry : networkToCreate.getNamespaces().entrySet()) {
			final Namespace namespace = namespaceEntry.getValue();
			final String jdexId = namespaceEntry.getKey();
			createNamespace(newNetwork, namespace, jdexId, networkIndex);
		}
	}

	private void createNamespaces(final Network networkToCreate,
			EquivalenceFinder equivalenceFinder) {
		for (final Entry<String, Namespace> namespaceEntry : networkToCreate.getNamespaces().entrySet()) {
			final Namespace namespace = namespaceEntry.getValue();
			final String jdexId = namespaceEntry.getKey();
			INamespace ns = equivalenceFinder.getNamespace(namespace, jdexId);
			if (null == ns)
				createNamespace(equivalenceFinder.getTargetNetwork(), namespace,
						jdexId,
						equivalenceFinder.getNetworkIndex());
		}
	}

	private void createNamespace(final INetwork newNetwork,
			final Namespace namespace,
			final String jdexId,
			final Map<String, VertexFrame> networkIndex) {
		final INamespace newNamespace = _orientDbGraph.addVertex(
				"class:namespace", INamespace.class);
		newNamespace.setJdexId(jdexId);

		final String prefix = namespace.getPrefix();
		if (prefix != null && !prefix.isEmpty())
			newNamespace.setPrefix(prefix);

		newNamespace.setUri(namespace.getUri());
		newNetwork.addNamespace(newNamespace);
		networkIndex.put(namespace.getJdexId(), newNamespace);
	}


	/**************************************************************************
	 * TERMS
	 * 
	 * Maps terms in network model object to terms in the network domain object
	 * 
	 * Note that term creation requires that the list of terms is ordered such that terms
	 * may only refer to other terms if those terms come earlier in the list.
	 * 
	 * The order of jdexIds in the network model object should be in order of dependency
	 * @throws NdexException 
	 * 
	 **************************************************************************/

	private void createBaseTerm(INetwork target, Network networkToCreate,
			BaseTerm term, String jdexId, Map<String, VertexFrame> networkIndex) throws NdexException {
		final IBaseTerm newBaseTerm = _orientDbGraph.addVertex(
				"class:baseTerm", IBaseTerm.class);
		newBaseTerm.setName(((BaseTerm) term).getName());
		newBaseTerm.setJdexId(jdexId);

		String namespaceJdexId = ((BaseTerm) term).getNamespace();

		if (namespaceJdexId != null && !namespaceJdexId.isEmpty()) {
			final VertexFrame namespace = networkIndex.get(namespaceJdexId);
			if (null == namespace)
				throw new NdexException("Namespace " + namespaceJdexId + " referenced by BaseTerm " + jdexId + " was not found in networkIndex cache");

			newBaseTerm.setTermNamespace((INamespace) namespace);
		}

		target.addTerm(newBaseTerm);
		networkIndex.put(newBaseTerm.getJdexId(), newBaseTerm);

		// TODO: remove this when the handling of metadata is finalized.
		//       (not currently used)
		// If the base term is also used as a metaterm, add it now
		if (networkToCreate.getMetaterms().containsValue(term)) {
			for (final Entry<String, BaseTerm> metaterm : networkToCreate
					.getMetaterms().entrySet()) {
				if (metaterm.equals(term)) {
					target.addMetaterm(metaterm.getKey(),
							newBaseTerm);
					break;
				}
			}
		}
		
	}

	private void createFunctionTerm(INetwork target, Network networkToCreate,
			FunctionTerm term, String jdexId,
			Map<String, VertexFrame> networkIndex) throws NdexException {
		final IFunctionTerm newFunctionTerm = _orientDbGraph.addVertex(
				"class:functionTerm", IFunctionTerm.class);
		newFunctionTerm.setJdexId(jdexId);
		
		final String termFunctionJdexId = ((FunctionTerm) term).getTermFunction();

		final VertexFrame function = networkIndex.get(termFunctionJdexId);
		if (null == function)
			throw new NdexException("BaseTerm " + termFunctionJdexId + " referenced as function of FunctionTerm " + jdexId + " was not found in networkIndex cache");

		newFunctionTerm.setTermFunc((IBaseTerm) function);

		List<ITerm> iParameters = new ArrayList<ITerm>();
		for (Entry<String, String> entry : ((FunctionTerm) term)
				.getParameters().entrySet()) {
			// All Terms mentioned as parameters should exist
			// (found or created) prior to the current term - 
			// it is a requirement of a JDEx format file.
			String parameterJdexId = entry.getValue();
			ITerm parameter = (ITerm) networkIndex.get(parameterJdexId);
			if (null == parameter) 
				throw new NdexException("BaseTerm " + parameterJdexId + " referenced as parameter of FunctionTerm " + jdexId + " was not found in networkIndex cache");

			iParameters.add(parameter);
			
		}

		newFunctionTerm.setTermParameters(iParameters);
		target.addTerm(newFunctionTerm);
		networkIndex.put(newFunctionTerm.getJdexId(), newFunctionTerm);
		
	}

	private boolean isFunctionTerm(Term term) {
		if (term.getTermType().equals("Function")) return true;
		return false;
	}

	private boolean isBaseTerm(Term term) {
		if (term.getTermType() == null 
				|| term.getTermType().isEmpty()
				|| term.getTermType().equals("Base"))
			return true;
		return false;
	}
	
	/**************************************************************************
	 * Creating terms with an equivalenceFinder
	 * 
	 * The equivalence finder determines whether a term is already in the
	 * target network. If it is, the term is cached for re-use
	 * 
	 * If no term is found, then a new term is created and cached.
	 * 
	 * A critical point is whether the jdexIds of terms in the source network can be 
	 * used when creating terms in the target network or whether a new 
	 * @throws NdexException 
	 * 
	 **************************************************************************/
	private void createTerms(final Network sourceNetwork,
			final EquivalenceFinder equivalenceFinder) throws NdexException {
		
		// Sort terms by dependency before creation because function terms can 
		// depend on each other.
		TermDependencyComparator tdc =  new TermDependencyComparator(sourceNetwork.getTerms());
        List<String> sortedTermIds = new ArrayList<String>(sourceNetwork.getTerms().keySet());
        Collections.sort(sortedTermIds, tdc);
		for (final String jdexId : sortedTermIds) {
			final Term term = sourceNetwork.getTerms().get(jdexId);
			if (isBaseTerm(term)){
				IBaseTerm iBaseTerm = equivalenceFinder.getBaseTerm((BaseTerm) term, jdexId);
				if (null == iBaseTerm)
					createBaseTerm(equivalenceFinder.getTargetNetwork(), sourceNetwork, (BaseTerm)term, jdexId, equivalenceFinder.getNetworkIndex());			
			} else if (isFunctionTerm(term)){
				IFunctionTerm iFunctionTerm = equivalenceFinder.getFunctionTerm((FunctionTerm) term, jdexId);
				if (null == iFunctionTerm)
					createFunctionTerm(equivalenceFinder.getTargetNetwork(), sourceNetwork, (FunctionTerm)term, jdexId, equivalenceFinder.getNetworkIndex());
			}
		}
	}



	/**************************************************************************
	 * Creating terms for an new network
	 * 
	 * No pre-existing terms, therefore no need to check 
	 * for equivalent terms pre-existing in network.
	 * 
	 * Terms must be created in order of dependency.
	 * 
	 * Hence they must be created in order of jdexId
	 * @throws NdexException 
	 * 
	 **************************************************************************/
	private void createTerms(final INetwork targetNetwork,
			final Network sourceNetwork,
			final Map<String, VertexFrame> networkIndex) throws NdexException {
		TermDependencyComparator tdc =  new TermDependencyComparator(sourceNetwork.getTerms());
        List<String> sortedTermIds = new ArrayList<String>(sourceNetwork.getTerms().keySet());
        Collections.sort(sortedTermIds, tdc);
		for (final String jdexId : sortedTermIds) {
			final Term term = sourceNetwork.getTerms().get(jdexId);
			if (isBaseTerm(term)){
				createBaseTerm(targetNetwork, sourceNetwork, (BaseTerm)term, jdexId, networkIndex);			
			} else if (isFunctionTerm(term)){
				createFunctionTerm(targetNetwork, sourceNetwork, (FunctionTerm)term, jdexId, networkIndex);
			}		
		}
	}
	


	/**************************************************************************
	 * CITATIONS
	 * 
	 * Maps citations in network model object to citations in the network domain object
	 * 
	 * 
	 **************************************************************************/
	private void createCitations(final INetwork targetNetwork,
			final Network sourceNetwork,
			final Map<String, VertexFrame> networkIndex) {
		for (final Entry<String, Citation> citationEntry : sourceNetwork
				.getCitations().entrySet()) {
			final Citation citation = citationEntry.getValue();
			final String jdexId = citationEntry.getKey();
			createCitation(targetNetwork, citation, jdexId, networkIndex);		
		}
	}

	private void createCitations(final Network sourceNetwork,
			final EquivalenceFinder equivalenceFinder) {
		for (final Entry<String, Citation> citationEntry : sourceNetwork
				.getCitations().entrySet()) {
			final Citation citation = citationEntry.getValue();
			final String jdexId = citationEntry.getKey();
			ICitation iCitation = equivalenceFinder.getCitation(citation, jdexId);
			if (null == iCitation)
				createCitation(equivalenceFinder.getTargetNetwork(), citation, jdexId, equivalenceFinder.getNetworkIndex());
		}
	}
	
	private void createCitation(final INetwork targetNetwork,
			final Citation citation,
			final String jdexId,
			final Map<String, VertexFrame> networkIndex){
		final ICitation newCitation = _orientDbGraph.addVertex(
				"class:citation", ICitation.class);
		newCitation.setJdexId(jdexId);
		newCitation.setTitle(citation.getTitle());
		newCitation.setIdentifier(citation.getIdentifier());
		newCitation.setType(citation.getType());
		newCitation.setContributors(citation.getContributors());

		targetNetwork.addCitation(newCitation);
		networkIndex.put(newCitation.getJdexId(), newCitation);
	}

	/**************************************************************************
	 * SUPPORTS
	 * 
	 * Maps supports in network model object to supports in the network domain object
	 * @throws NdexException 
	 * 
	 * 
	 **************************************************************************/
	private void createSupports(final INetwork targetNetwork,
			final Network sourceNetwork,
			final Map<String, VertexFrame> networkIndex) throws NdexException {
		for (final Entry<String, Support> supportEntry : sourceNetwork
				.getSupports().entrySet()) {
			final Support support = supportEntry.getValue();
			final String jdexId = supportEntry.getKey();
			createSupport(targetNetwork, support, jdexId, networkIndex);
			
		}
	}
	
	private void createSupports(final Network sourceNetwork,
			final EquivalenceFinder equivalenceFinder) throws NdexException {
		for (final Entry<String, Support> supportEntry : sourceNetwork
				.getSupports().entrySet()) {
			final Support support = supportEntry.getValue();
			final String jdexId = supportEntry.getKey();
			ISupport iSupport = equivalenceFinder.getSupport(support, jdexId);
			if (null == iSupport)
				createSupport(equivalenceFinder.getTargetNetwork(), support, jdexId, equivalenceFinder.getNetworkIndex());
			
		}
	}
	
	private void createSupport(final INetwork targetNetwork,
			final Support support,
			final String jdexId,
			final Map<String, VertexFrame> networkIndex) throws NdexException{
		final ISupport newSupport = _orientDbGraph.addVertex(
				"class:support", ISupport.class);
		newSupport.setJdexId(jdexId);
		newSupport.setText(support.getText());
		
		if (null != support.getCitation()){
			ICitation iCitation = (ICitation) networkIndex.get(support.getCitation());
			if (null == iCitation)
				throw new NdexException("Citation " + support.getCitation() + " referenced by support " + jdexId + " was not found in networkIndex cache");
			newSupport.setSupportCitation(iCitation);
		}

		targetNetwork.addSupport(newSupport);
		networkIndex.put(newSupport.getJdexId(), newSupport);
		
	}

	/**************************************************************************
	 * NODES
	 * 
	 * Maps nodes in network model object to nodes in the network domain object
	 * @throws NdexException 
	 * 
	 * 
	 **************************************************************************/
	private void createNodes(final Network sourceNetwork,
			final EquivalenceFinder equivalenceFinder) throws NdexException {
		int nodeCount = 0;

		for (final Entry<String, Node> nodeEntry : sourceNetwork.getNodes()
				.entrySet()) {
			final Node node = nodeEntry.getValue();
			final String jdexId = nodeEntry.getKey();
			INode iNode = equivalenceFinder.getNode(node, jdexId);
			if (null == iNode){
				createNode(equivalenceFinder.getTargetNetwork(), node, jdexId, equivalenceFinder.getNetworkIndex());
				nodeCount++;
			}					
		}
		equivalenceFinder.getTargetNetwork().setNdexNodeCount(nodeCount);
	}
	
	private void createNodes(final INetwork targetNetwork,
			final Network sourceNetwork,
			final Map<String, VertexFrame> networkIndex) throws NdexException {
		int nodeCount = 0;

		for (final Entry<String, Node> nodeEntry : sourceNetwork.getNodes()
				.entrySet()) {
			final Node node = nodeEntry.getValue();
			final String jdexId = nodeEntry.getKey();

			createNode(targetNetwork, node, jdexId, networkIndex);
			nodeCount++;			
		}

		targetNetwork.setNdexNodeCount(nodeCount);
	}
	
	private void createNode(final INetwork targetNetwork,
			final Node node,
			final String jdexId,
			final Map<String, VertexFrame> networkIndex) throws NdexException{
		final INode iNode = _orientDbGraph.addVertex("class:node",
				INode.class);
		iNode.setJdexId(jdexId);
		if (null != node.getName())
			iNode.setName(node.getName());

		final ITerm representedITerm = (ITerm) networkIndex.get(node.getRepresents());
		if (null == representedITerm)
			throw new NdexException("Term " + node.getRepresents() + " referenced by node " + jdexId + " was not found in networkIndex cache");

		iNode.setRepresents(representedITerm);

		targetNetwork.addNdexNode(iNode);
		networkIndex.put(iNode.getJdexId(), iNode);
		
	}

	/**************************************************************************
	 * EDGES
	 * 
	 * Maps edges in network model object to edges in the network domain object
	 * @throws NdexException 
	 * 
	 * 
	 **************************************************************************/
	private void createEdges(final Network sourceNetwork,
			final EquivalenceFinder equivalenceFinder) throws NdexException {
		int edgeCount = 0;

		for (final Entry<String, Edge> edgeEntry : sourceNetwork.getEdges()
				.entrySet()) {
			final Edge edge = edgeEntry.getValue();
			final String jdexId = edgeEntry.getKey();
			IEdge iEdge = equivalenceFinder.getEdge(edge, jdexId);
			if (null == iEdge){
				createEdge(equivalenceFinder.getTargetNetwork(), edge, jdexId, equivalenceFinder.getNetworkIndex());
				edgeCount++;
			}
		}
		equivalenceFinder.getTargetNetwork().setNdexEdgeCount(edgeCount);
	}
	
	private void createEdges(final INetwork targetNetwork,
			final Network sourceNetwork,
			final Map<String, VertexFrame> networkIndex) throws NdexException {
		int edgeCount = 0;

		for (final Entry<String, Edge> edgeEntry : sourceNetwork.getEdges()
				.entrySet()) {
			final Edge edge = edgeEntry.getValue();
			final String jdexId = edgeEntry.getKey();	
			createEdge(targetNetwork, edge, jdexId, networkIndex);
			edgeCount++;
		}
		targetNetwork.setNdexEdgeCount(edgeCount);
	}
	
	private void createEdge(final INetwork targetNetwork,
			final Edge edge,
			final String jdexId,
			final Map<String, VertexFrame> networkIndex) throws NdexException{
		final IEdge newEdge = _orientDbGraph.addVertex("class:edge",
				IEdge.class);
		newEdge.setJdexId(jdexId);

		final INode subjectNode = (INode) networkIndex.get(edge.getS());
		if (null == subjectNode)
			throw new NdexException("Node " + edge.getS() + " referenced as subject  of Edge " + jdexId + " was not found in networkIndex cache");
		newEdge.setSubject(subjectNode);

		final IBaseTerm predicateTerm = (IBaseTerm) networkIndex.get(edge.getP());
		if (null == predicateTerm)
			throw new NdexException("BaseTerm " + edge.getP() + " referenced as predicate  of Edge " + jdexId + " was not found in networkIndex cache");
		newEdge.setPredicate(predicateTerm);

		final INode objectNode = (INode) networkIndex.get(edge.getO());
		if (null == objectNode)
			throw new NdexException("Node " + edge.getO() + " referenced as object  of Edge " + jdexId + " was not found in networkIndex cache");
		newEdge.setObject(objectNode);

		for (final String citationId : edge.getCitations())
			newEdge.addCitation((ICitation) networkIndex.get(citationId));

		for (final String supportId : edge.getSupports())
			newEdge.addSupport((ISupport) networkIndex.get(supportId));

		targetNetwork.addNdexEdge(newEdge);
		networkIndex.put(newEdge.getJdexId(), newEdge);
	}


	/**************************************************************************
	 * 
	 * Finds terms in network by name
	 * 
	 * TODO: review implementation
	 * 
	 * 
	 **************************************************************************/
	private List<Term> getBaseTermsByName(INetwork network, String baseTermName)
			throws NdexException {
		final List<Term> foundTerms = new ArrayList<Term>();
		for (final ITerm networkTerm : network.getTerms()) {
			if (networkTerm instanceof IBaseTerm) {
				if (baseTermName.equals(((IBaseTerm) networkTerm).getName())) {
					final Term term = new BaseTerm((IBaseTerm) networkTerm);
					foundTerms.add(term);
				}
			}
		}

		return foundTerms;
	}

	/**************************************************************************
	 * 
	 * Constructs and returns a self-sufficient network based on a set of edges
	 * 
	 * Finds all referenced nodes, terms, supports, and citations for the edges.
	 * 
	 * 
	 **************************************************************************/
	private static Network getNetworkBasedOnFoundEdges(
			final List<IEdge> foundEdges, final INetwork network) {
		final Set<INode> requiredINodes = getEdgeNodes(foundEdges);
		final Set<ITerm> requiredITerms = getEdgeTerms(foundEdges,
				requiredINodes);
		final Set<ISupport> requiredISupports = getEdgeSupports(foundEdges);
		final Set<ICitation> requiredICitations = getEdgeCitations(foundEdges,
				requiredISupports);
		final Set<INamespace> requiredINamespaces = getTermNamespaces(requiredITerms);

		// Now create the output network
		final Network networkByEdges = new Network();
		networkByEdges.setDescription(network.getDescription());
		networkByEdges.setMetadata(network.getMetadata());
		networkByEdges.setName(network.getName());

		if (network.getMetaterms() != null) {
			for (Entry<String, IBaseTerm> metaterm : network.getMetaterms()
					.entrySet())
				networkByEdges.getMetaterms().put(metaterm.getKey(),
						new BaseTerm(metaterm.getValue()));
		}

		for (final IEdge edge : foundEdges)
			networkByEdges.getEdges().put(edge.getJdexId(), new Edge(edge));

		networkByEdges.setEdgeCount(foundEdges.size());

		for (final INode node : requiredINodes)
			networkByEdges.getNodes().put(node.getJdexId(), new Node(node));

		networkByEdges.setNodeCount(requiredINodes.size());

		for (final ITerm term : requiredITerms) {
			if (term instanceof IBaseTerm)
				networkByEdges.getTerms().put(term.getJdexId(),
						new BaseTerm((IBaseTerm) term));
			else if (term instanceof IFunctionTerm)
				networkByEdges.getTerms().put(term.getJdexId(),
						new FunctionTerm((IFunctionTerm) term));
		}

		for (final INamespace namespace : requiredINamespaces)
			networkByEdges.getNamespaces().put(namespace.getJdexId(),
					new Namespace(namespace));

		for (final ISupport support : requiredISupports)
			networkByEdges.getSupports().put(support.getJdexId(),
					new Support(support));

		for (final ICitation citation : requiredICitations)
			networkByEdges.getCitations().put(citation.getJdexId(),
					new Citation(citation));

		return networkByEdges;
	}

	
	/**************************************************************************
	 * 
	 * Not currently used, may not be necessary
	 * 
	 * 
	 **************************************************************************/
	private static Network getNetworkBasedOnFoundNodes(
			final List<INode> foundINodes, final INetwork network) {
		final Set<ITerm> requiredITerms = getNodeTerms(foundINodes);
		final Set<INamespace> requiredINamespaces = getTermNamespaces(requiredITerms);

		// Now create the output network
		final Network networkByNodes = new Network();
		networkByNodes.setDescription(network.getDescription());
		networkByNodes.setMetadata(network.getMetadata());
		networkByNodes.setName(network.getName());

		if (network.getMetaterms() != null) {
			for (Entry<String, IBaseTerm> metaterm : network.getMetaterms()
					.entrySet())
				networkByNodes.getMetaterms().put(metaterm.getKey(),
						new BaseTerm(metaterm.getValue()));
		}

		for (final INode node : foundINodes)
			networkByNodes.getNodes().put(node.getJdexId(), new Node(node));

		for (final ITerm term : requiredITerms) {
			if (term instanceof IBaseTerm)
				networkByNodes.getTerms().put(term.getJdexId(),
						new BaseTerm((IBaseTerm) term));
			else if (term instanceof IFunctionTerm)
				networkByNodes.getTerms().put(term.getJdexId(),
						new FunctionTerm((IFunctionTerm) term));
		}

		for (final INamespace requiredNamespace : requiredINamespaces)
			networkByNodes.getNamespaces().put(requiredNamespace.getJdexId(),
					new Namespace(requiredNamespace));

		return networkByNodes;
	}

	private static Set<INode> getEdgeNodes(final List<IEdge> edges) {
		final Set<INode> edgeNodes = new HashSet<INode>();

		for (final IEdge edge : edges) {
			edgeNodes.add(edge.getSubject());
			edgeNodes.add(edge.getObject());
		}

		return edgeNodes;
	}

	private static Set<ITerm> getEdgeTerms(final List<IEdge> edges,
			final Collection<INode> nodes) {
		final Set<ITerm> edgeTerms = new HashSet<ITerm>();

		for (final IEdge edge : edges)
			edgeTerms.add(edge.getPredicate());

		for (final INode node : nodes) {
			if (node.getRepresents() != null)
				addTermAndFunctionalDependencies(node.getRepresents(),
						edgeTerms);
			if (node.getAliases() != null) {
				for (ITerm iTerm : node.getAliases()) {
					addTermAndFunctionalDependencies(iTerm, edgeTerms);
				}
			}
			if (node.getRelatedTerms() != null) {
				for (ITerm iTerm : node.getRelatedTerms()) {
					addTermAndFunctionalDependencies(iTerm, edgeTerms);
				}
			}
		}

		return edgeTerms;
	}

	private static Set<ITerm> getNodeTerms(final Collection<INode> nodes) {
		final Set<ITerm> nodeTerms = new HashSet<ITerm>();

		for (final INode node : nodes) {
			if (node.getRepresents() != null)
				addTermAndFunctionalDependencies(node.getRepresents(),
						nodeTerms);
		}

		return nodeTerms;
	}

	private static Set<ISupport> getEdgeSupports(final List<IEdge> edges) {
		final Set<ISupport> edgeSupports = new HashSet<ISupport>();

		for (final IEdge edge : edges) {
			for (final ISupport support : edge.getSupports())
				edgeSupports.add(support);
		}

		return edgeSupports;
	}

	private static Set<ICitation> getEdgeCitations(final List<IEdge> edges,
			final Collection<ISupport> supports) {
		final Set<ICitation> edgeCitations = new HashSet<ICitation>();
		for (final IEdge edge : edges) {
			for (final ICitation citation : edge.getCitations())
				edgeCitations.add(citation);
		}

		for (final ISupport support : supports) {
			if (support.getSupportCitation() != null)
				edgeCitations.add(support.getSupportCitation());
		}

		return edgeCitations;
	}

	private static Set<INamespace> getTermNamespaces(
			final Set<ITerm> requiredITerms) {
		final Set<INamespace> namespaces = new HashSet<INamespace>();

		for (final ITerm term : requiredITerms) {
			if (term instanceof IBaseTerm
					&& ((IBaseTerm) term).getTermNamespace() != null)
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
	private boolean hasPermission(Network targetNetwork,
			Permissions requiredPermissions) {
		for (Membership networkMembership : this.getLoggedInUser()
				.getNetworks()) {
			if (networkMembership.getResourceId().equals(targetNetwork.getId())
					&& networkMembership.getPermissions().compareTo(
							requiredPermissions) > -1)
				return true;
		}

		return false;
	}

	private List<IEdge> neighborhoodQuery(final INetwork network,
			final NetworkQueryParameters queryParameters)
			throws IllegalArgumentException {
		final List<IEdge> foundEdges = new ArrayList<IEdge>();

		// Make a SearchSpec based on the NetworkQueryParameters, verifying &
		// converting term ids in the process
		final SearchSpec searchSpec = new SearchSpec(queryParameters);

		final Set<OrientVertex> orientEdges = NetworkQueries.INSTANCE
				.searchNeighborhoodByTerm(_orientDbGraph.getBaseGraph(),
						(OrientVertex) network.asVertex(), searchSpec);
		for (final OrientVertex edge : orientEdges)
			foundEdges.add(_orientDbGraph.getVertex(edge, IEdge.class));

		return foundEdges;
	}

	private List<IEdge> neighborhoodNodeQuery(final INetwork network,
			List<INode> startingNodes, int searchDepth, String searchType)
			throws IllegalArgumentException {
		final List<IEdge> foundEdges = new ArrayList<IEdge>();
		// final List<OrientVertex> nodes = new ArrayList<OrientVertex>();
		// for (INode iNode : startingNodes){
		// nodes.add((OrientVertex) iNode.asVertex());
		// }

		OIdentifiable[] nodes = new OIdentifiable[startingNodes.size()];
		for (int index = 0; index < startingNodes.size(); index++) {
			INode startingNode = startingNodes.get(index);
			final ORID rid = new ORecordId(startingNode.asVertex().getId()
					.toString());
			nodes[index] = rid;
		}

		final SearchType searchSpecSearchType = SearchType.valueOf(searchType);

		final Set<OrientVertex> orientEdges = NetworkQueries.INSTANCE
				.searchNeighborhoodByNodes(_orientDbGraph.getBaseGraph(),
						(OrientVertex) network.asVertex(), nodes, searchDepth,
						searchSpecSearchType);
		for (final OrientVertex edge : orientEdges)
			foundEdges.add(_orientDbGraph.getVertex(edge, IEdge.class));

		return foundEdges;
	}

	/**************************************************************************
	 * Parses metadata and metaterm parameters using the given regex and removes
	 * them from the search parameters.
	 * 
	 * @param searchParameters
	 *            The search parameters.
	 * @param metaRegex
	 *            The regex pattern to use for parsing parameters.
	 * @return An ArrayList containing the search parameters.
	 **************************************************************************/
	private ArrayList<MetaParameter> parseMetaParameters(
			final SearchParameters searchParameters, final Pattern metaRegex) {
		final ArrayList<MetaParameter> metadataParameters = new ArrayList<MetaParameter>();
		final Matcher metadataMatches = metaRegex.matcher(searchParameters
				.getSearchString());

		if (!metadataMatches.find())
			return metadataParameters;

		for (int groupIndex = 0; groupIndex < metadataMatches.groupCount(); groupIndex += 3) {
			metadataParameters.add(new MetaParameter(metadataMatches
					.group(groupIndex + 1), metadataMatches.group(
					groupIndex + 2).charAt(0), metadataMatches.group(
					groupIndex + 3).substring(1,
					metadataMatches.group(groupIndex + 3).length() - 1)));

			searchParameters.setSearchString(searchParameters.getSearchString()
					.replace(metadataMatches.group(groupIndex), ""));
		}

		return metadataParameters;
	}

	/**************************************************************************
	 * Parses (base) terms from the search parameters.
	 * 
	 * @param searchParameters
	 *            The search parameters.
	 * @return A string containing additional SQL to add to the WHERE clause.
	 **************************************************************************/
	private String parseTermParameters(final SearchParameters searchParameters) {
		final Pattern termRegex = Pattern.compile("terms:\\{(.+)\\}");
		final Matcher termMatcher = termRegex.matcher(searchParameters
				.getSearchString());

		if (termMatcher.find()) {
			final String terms[] = termMatcher.group(1).split(",");
			final StringBuilder termConditions = new StringBuilder();
			termConditions
					.append("  AND @RID IN (SELECT in_networkTerms FROM (TRAVERSE out_networkTerms FROM Network) \n");

			for (final String term : terms) {
				final String namespaceAndTerm[] = term.split(":");
				if (namespaceAndTerm.length != 2)
					throw new IllegalArgumentException(
							"Error parsing terms from: \""
									+ termMatcher.group(0) + "\".");

				searchParameters.setSearchString(searchParameters
						.getSearchString().replace(termMatcher.group(0), ""));

				if (termConditions.length() < 100)
					termConditions.append("    WHERE (");
				else
					// TODO: Originally the idea here was to perform a UNION
					// query against the network get all terms, unfortunately,
					// while OrientDB has a UNION function, it's more of an
					// array concatenation as opposed to merging query results.
					// Instead it seems that OrientDB has a CONTAINS operator
					// that might do the trick, otherwise multi-term searching
					// will be very, very tricky. Another alternative would be
					// using custom Java functions, which OrientDB supports as
					// being usable within a SQL query. Once a solution has
					// been discovered, replace the commented line below with
					// whatever is needed to join all the conditions together.
					break;
				// termConditions.append("\n      AND ");

				termConditions
						.append("out_baseTermNamespace.prefix.toLowerCase() = '");
				termConditions.append(namespaceAndTerm[0].trim().toLowerCase());
				termConditions.append("' AND name.toLowerCase() = '");
				termConditions.append(namespaceAndTerm[1].trim().toLowerCase());
				termConditions.append("') ");
			}

			termConditions.append(") \n");
			return termConditions.toString();
		}

		return null;
	}

	/**************************************************************************
	 * join together a list of strings to create a quoted, comma-separated
	 * string
	 * 
	 * @param strings
	 * @return resultString
	 **************************************************************************/
	private String joinStringsToCsv(Collection<String> strings) {
		String resultString = "";
		for (final String string : strings) {
			resultString += "'" + string + "',";
		}
		resultString = resultString.substring(0, resultString.length() - 1);
		return resultString;

	}
	
	/**************************************************************************
	 * join together an array of strings to create a quoted, comma-separated
	 * string
	 * 
	 * @param strings
	 * @return resultString
	 **************************************************************************/
	private String joinStringsToCsv(String[] strings) {
		String resultString = "";
		for (final String string : strings) {
			resultString += "'" + string + "',";
		}
		resultString = resultString.substring(0, resultString.length() - 1);
		return resultString;

	}

	/**************************************************************************
	 * process a list of VertexFrames to create a quoted, comma-separated string
	 * of their ids - suitable for incorporation in a query
	 * 
	 * @param vertexFrames
	 * @return resultString
	 **************************************************************************/
	private String joinBaseTermIdsToCsv(List<IBaseTerm> iBaseTerms) {
		String resultString = "";
		for (final IBaseTerm iBaseTerm : iBaseTerms) {
			resultString += iBaseTerm.asVertex().getId().toString() + ", ";
		}
		resultString = resultString.substring(0, resultString.length() - 2);
		return resultString;
	}
}
