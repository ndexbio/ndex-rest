package org.ndexbio.rest.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Namespace;
import org.ndexbio.rest.models.Network;
import org.ndexbio.rest.models.Term;
import org.ndexbio.rest.models.BaseTerm;
import org.ndexbio.rest.models.FunctionTerm;
import org.ndexbio.rest.models.Node;
import org.ndexbio.rest.models.Edge;
import org.ndexbio.rest.models.Citation;
import org.ndexbio.rest.models.Support;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.VertexFrame;

@Path("/networks")
public class NetworkService extends NdexService {
	

	/*
	 * public operation to create a new Network
	 */
	@PUT
	@Produces("application/json")
	public Network createNetwork(final String ownerId, final Network network)
			throws Exception {
		if (Strings.isNullOrEmpty(ownerId) || null == network) {
			throw new NdexException(
					"createNetwork invoke with null or ivalid parameters");
		}
		// Check for existence of User specified as owner of new network
		ORID userRid = RidConverter.convertToRid(ownerId);

		final IUser networkOwner = _orientDbGraph.getVertex(userRid,
				IUser.class);
		if (networkOwner == null) {
			throw new ObjectNotFoundException("User", ownerId);
		}
		
		final Map<String, VertexFrame> networkIndex = Maps.newHashMap();

		// Create the network object in the database managed by an iNetwork
		final INetwork iNetwork = _orientDbGraph.addVertex("class:network",
				INetwork.class);
		// Assert ownership
		networkOwner.addOwnedNetwork(iNetwork);

		try {
		    iNetwork.setFormat(network.getFormat());
		    iNetwork.setSource(network.getSource());
		    iNetwork.setTitle(network.getTitle());
		    
			// First create all namespaces used by the network
			createNamespaces(iNetwork, network, networkIndex);
			// Then create terms which may reference the namespaces
			// The terms are created in order of reference - later terms may
			// refer to earlier terms
			createTerms(iNetwork, network,networkIndex);
			// Then create nodes that may reference terms
			createNodes(iNetwork, network, networkIndex);
			// Then create edges that reference terms nodes
			createEdges(iNetwork, network, networkIndex);
			// Then create citations that reference edges
			createCitations(iNetwork, network,networkIndex);
			// Then create supports that reference edges and citations
			createSupports(iNetwork, network, networkIndex);
			
			_orientDbGraph.getBaseGraph().commit();
		} catch (Exception e)

		{
			handleOrientDbException(e);
		} finally {
			closeOrientDbConnection();
		}
		return network;
	}

	/*
	 * map namespaces from network model object to namespaces in the network
	 * domain object
	 */
	private void createNamespaces(INetwork iNetwork, Network network,
			Map<String, VertexFrame> networkIndex) {
		Collection<Namespace> namespaceList = network.getNamespaces().values();
		for (Namespace namespace : namespaceList) {
			final INamespace ins = _orientDbGraph.addVertex("class:namespace",
					INamespace.class);
			ins.setJdexId(namespace.getJdexId());
			String prefix = namespace.getPrefix();
			if (!Strings.isNullOrEmpty(prefix)) {
				ins.setPrefix(prefix);
			}
			ins.setUri(namespace.getUri());
			iNetwork.addNamespace(ins);
			networkIndex.put(namespace.getJdexId(), ins);
		}

	}

	/*
	 * map terms in network model object to terms in the network domain object
	 * 
	 * Note that this requires that the list of terms is ordered such that terms
	 * may only refer to other terms if those terms come earlier in the list.
	 * 
	 * mod 25Nov2013
	 * use JdexIds for reference in lieu of object aggregation
	 */
	private void createTerms(INetwork iNetwork, Network network,
			Map<String, VertexFrame> networkIndex) {
		
		for (Map.Entry<String,Term> termEntry: network.getTerms().entrySet() ){
			Term term = termEntry.getValue();
			if (term instanceof BaseTerm) {
				final IBaseTerm iBaseTerm = _orientDbGraph.addVertex(
						"class:baseTerm", IBaseTerm.class);
				iBaseTerm.setName(((BaseTerm) term).getName());
				iBaseTerm.setJdexId(termEntry.getKey());
				String nsJdexId = ((BaseTerm) term).getNamespace();
				if (!Strings.isNullOrEmpty(nsJdexId)) {
					INamespace ins = (INamespace) networkIndex.get(nsJdexId);
					iBaseTerm.addNamespace(ins);
					// TODO check for case where no ins found in networkIndex
				}
				iNetwork.addTerm(iBaseTerm);
				networkIndex.put(iBaseTerm.getJdexId(), iBaseTerm);

			} else if (term instanceof FunctionTerm) {
				FunctionTerm fterm = (FunctionTerm) term;
				final IFunctionTerm iFunctionTerm = _orientDbGraph.addVertex(
						"class:functionTerm", IFunctionTerm.class);
				iFunctionTerm.setJdexId(termEntry.getKey());
				IBaseTerm function = (IBaseTerm) networkIndex.get(fterm.getTermFunction());
				iFunctionTerm.setTermFunction(function);
				// TODO check for case where no function found in networkIndex

				for (Map.Entry<Integer, String> entry : fterm.getTextParameters().entrySet()) {
					Integer index = (Integer) entry.getKey();
					String param = (String) entry.getValue();
					iFunctionTerm.getTextParameters().put(index, param);
				}

				for (Map.Entry<Integer, String> entry : fterm.getTermParameters().entrySet()) {
					Integer index = (Integer) entry.getKey();
					String termJDExId = (String) entry.getValue();
					ITerm parameterTerm = (ITerm) networkIndex.get(termJDExId);
					iFunctionTerm.getTermParameters().put(index, parameterTerm);
				}

				iNetwork.addTerm(iFunctionTerm);
				networkIndex.put(iFunctionTerm.getJdexId(), iFunctionTerm);

			} else
				continue;

		}

	}

	private void createNodes(INetwork iNetwork, Network network,
			Map<String, VertexFrame> networkIndex) {
		Integer nodesCount = 0;
		
		for (Map.Entry<String, Node> nodeEntry : network.getNodes().entrySet()) {
			Node node = nodeEntry.getValue();
			
			INode iNode = _orientDbGraph.addVertex("class:node", INode.class);
			iNode.setJdexId(nodeEntry.getKey());
			nodesCount++;
			String termId = node.getRepresents();
			
			
			ITerm representedITerm = (ITerm) networkIndex.get(termId);
			iNode.setRepresents(representedITerm);
			iNetwork.addNdexNode(iNode);
			networkIndex.put(iNode.getJdexId(), iNode);
		}
		iNetwork.setNdexNodeCount(nodesCount);
	}

	private void createEdges(INetwork iNetwork, Network network,
			Map<String, VertexFrame> networkIndex) {
		Integer edgesCount = 0;
		for (Map.Entry<String, Edge> edgeEntry:  network.getEdges().entrySet()) {
			Edge edge= edgeEntry.getValue();
			IEdge iEdge = _orientDbGraph.addVertex("class:edge", IEdge.class);
			iEdge.setJdexId(edgeEntry.getKey());
			edgesCount++;

			INode subjectINode = (INode) networkIndex.get(edge.getSubjectId());
			iEdge.setSubject(subjectINode);			
			IBaseTerm predicateITerm = (IBaseTerm) networkIndex.get(edge.getPredicateId());
			iEdge.setPredicate(predicateITerm);			
			INode objectINode = (INode) networkIndex.get(edge.getObjectId());
			iEdge.setObject(objectINode);

			iNetwork.addNdexEdge(iEdge);
			networkIndex.put(iEdge.getJdexId(), iEdge);
		}
		iNetwork.setNdexEdgeCount(edgesCount);

	}

	private void createCitations(INetwork iNetwork, Network network,
			Map<String, VertexFrame> networkIndex) {
		for (Map.Entry<String, Citation> citationEntry : network.getCitations().entrySet()) {
			ICitation iCitation = _orientDbGraph.addVertex("class:citation",
					ICitation.class);
			Citation citation = citationEntry.getValue();
			iCitation.setJdexId(citationEntry.getKey());
			iCitation.setTitle(citation.getTitle());
			iCitation.setIdentifier(citation.getIdentifier());
			iCitation.setType(citation.getType());
			iCitation.setContributors(citation.getContributors());

			for (String edgeId : citation.getEdges()) {
				IEdge citationEdge = (IEdge) networkIndex.get(edgeId);
				iCitation.addNdexEdge(citationEdge);
			}
			iNetwork.addCitation(iCitation);
			networkIndex.put(iCitation.getJdexId(), iCitation);
		}

	}

	private void createSupports(INetwork iNetwork, Network network,
			Map<String, VertexFrame> networkIndex) {
		for (Map.Entry<String, Support> supportEntry : network.getSupports().entrySet()) {
			ISupport iSupport = _orientDbGraph.addVertex("class:support",
					ISupport.class);
			Support support = supportEntry.getValue();
			iSupport.setJdexId(supportEntry.getKey());
			iSupport.setText(support.getText());

			for (String edgeId : support.getEdges()) {
				IEdge supportEdge = (IEdge) networkIndex.get(edgeId);
				iSupport.addNdexEdge(supportEdge);
			}

			if (!Strings.isNullOrEmpty(support.getCitationId() )) {
				ICitation citation = (ICitation) networkIndex.get(support.getCitationId());
				iSupport.setCitation(citation);
			}

			iNetwork.addSupport(iSupport);
			networkIndex.put(iSupport.getJdexId(), iSupport);
		}

	}

	@DELETE
	@Path("/{networkId}")
	@Produces("application/json")
	public void deleteNetwork(@PathParam("networkId") final String networkJid)
			throws NdexException {
		ORID networkRid = RidConverter.convertToRid(networkJid);

		final Vertex networkToDelete = _orientDbGraph.getVertex(networkRid);
		if (networkToDelete == null)
			return;

		try {
			_orientDbGraph.removeVertex(networkToDelete);
			_orientDbGraph.getBaseGraph().commit();

			// TODO: Is this necessary? (Deleting a network should delete all
			// children)
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
			if (_orientDbGraph != null)
				_orientDbGraph.getBaseGraph().rollback();

			throw e;
		} finally {
			if (_ndexDatabase != null)
				_ndexDatabase.close();
		}
	}

	/*
	 * Find networks based on a search expression
	 */
	@GET
	@Path("/networks/{search}/{offset}/{limit}")
	@Produces("application/json")
	public Collection<Network> findNetworks(
			@PathParam("search") String searchString,
			@PathParam("offset") final String offsetParam,
			@PathParam("limit") final String limitParam) throws NdexException {
		Collection<Network> foundNetworks = Lists.newArrayList();

		if (Strings.isNullOrEmpty(searchString)) {
			return foundNetworks;
		}
		Integer offset = Ints.tryParse(offsetParam);
		Integer limit = Ints.tryParse(limitParam);
		int start = 0;
		if (null != offset && null != limit) {
			start = offset.intValue() * limit.intValue();
		}

		searchString = searchString.toUpperCase().trim();

		String where_clause = "";
		if (searchString.length() > 0)
			where_clause = " where properties.title.toUpperCase() like '%"
					+ searchString
					+ "%' OR properties.description.toUpperCase() like '%"
					+ searchString + "%'";

		final String query = "select from Network " + where_clause
				+ " order by creation_date desc skip " + start + " limit "
				+ limit;
		List<ODocument> networkDocumentList = _orientDbGraph.getBaseGraph()
				.getRawGraph().query(new OSQLSynchQuery<ODocument>(query));
		for (ODocument document : networkDocumentList) {
			INetwork iNetwork = _orientDbGraph.getVertex(document,
					INetwork.class);
			foundNetworks.add(new Network(iNetwork));
		}

		return foundNetworks;

	}

	/*
	 * Get a network with only its properties set.
	 */
	@GET
	@Path("/{networkId}")
	@Produces("application/json")
	public Network getNetwork(@PathParam("networkId") final String networkJid)
			throws NdexException {
		ORID networkRid = RidConverter.convertToRid(networkJid);
		final INetwork network = _orientDbGraph.getVertex(networkRid,
				INetwork.class);
		if (network == null)
			return null;
		else
			return new Network(network);
	}
	

	/*
	 * Set the properties of a network 
	 */
	@POST
	@Produces("application/json")
	public void updateNetwork(final Network updatedNetwork)
			throws NdexException {
		final INetwork network = _orientDbGraph.getVertex(
				RidConverter.convertToRid(updatedNetwork.getId()),
				INetwork.class);
		if (network == null)
			throw new ObjectNotFoundException("Network", updatedNetwork.getId());

		try {
		    network.setSource(updatedNetwork.getSource());
		    network.setTitle(updatedNetwork.getTitle());
		    
			_orientDbGraph.getBaseGraph().commit();
		} catch (Exception e) {
			if (_orientDbGraph != null)
				_orientDbGraph.getBaseGraph().rollback();

			throw e;
		} finally {
			if (_ndexDatabase != null)
				_ndexDatabase.close();
		}
	}

	/*
	 * Get a network containing a "page" of Edges and their supporting nodes,terms, namespaces, supports, and citations
	 */
	@GET
	@Path("/{networkId}/edges")
	@Produces("application/json")
	public Network getEdges(@PathParam("networkId") final String networkJid,
			Integer limit, Integer offset) throws NdexException {
		ORID networkRid = RidConverter.convertToRid(networkJid);
		final INetwork network = _orientDbGraph.getVertex(networkRid,
				INetwork.class);
		if (network == null) {
			return null;
		} else {
			Integer counter = 0;
			List<IEdge> foundIEdges = new ArrayList<IEdge>();
			Integer startIndex = limit * offset;

			for (IEdge networkEdge : network.getNdexEdges()) {
				if (counter >= startIndex) {
					foundIEdges.add(networkEdge);
				}
				counter++;
				if (counter >= startIndex + limit)
					break;
			}

			return getNetworkBasedOnFoundEdges(foundIEdges, network);
		}
	}
	
	/*
	 * Get a network containing a "page" of Nodes and their supporting terms and namespaces
	 */
	@GET
	@Path("/{networkId}/nodes")
	@Produces("application/json")
	public Network getNodes(@PathParam("networkId") final String networkJid,
			Integer limit, Integer offset) throws NdexException {
		ORID networkRid = RidConverter.convertToRid(networkJid);
		final INetwork network = _orientDbGraph.getVertex(networkRid,
				INetwork.class);
		if (network == null) {
			return null;
		} else {
			Integer counter = 0;
			List<INode> foundINodes = new ArrayList<INode>();
			Integer startIndex = limit * offset;

			for (INode networkNode : network.getNdexNodes()) {
				if (counter >= startIndex) {
					foundINodes.add(networkNode);
				}
				counter++;
				if (counter >= startIndex + limit)
					break;
			}

			return getNetworkBasedOnFoundNodes(foundINodes, network);
		}
	}

	private static Network getNetworkBasedOnFoundEdges(List<IEdge> foundIEdges,
			INetwork network) {

		Set<INode> requiredINodes = getEdgeNodes(foundIEdges);
		Set<ITerm> requiredITerms = getEdgeTerms(foundIEdges, requiredINodes);
		Set<ISupport> requiredISupports = getEdgeSupports(foundIEdges);
		Set<ICitation> requiredICitations = getEdgeCitations(foundIEdges,
				requiredISupports);
		Set<INamespace> requiredINamespaces = getTermNamespaces(requiredITerms);

		// Now create the output network
		Network networkByEdges = new Network();
        networkByEdges.setFormat(network.getFormat());
        networkByEdges.setSource(network.getSource());
        networkByEdges.setTitle(network.getTitle());

		for (IEdge edge : foundIEdges) {
			networkByEdges.getEdges().put(edge.getJdexId(), new Edge(edge));
		}

		for (INode node : requiredINodes) {
			networkByEdges.getNodes().put(node.getJdexId(), new Node(node));
		}

		for (ITerm term : requiredITerms) {
			if ( term instanceof IBaseTerm) {
				networkByEdges.getTerms().put(term.getJdexId(), new BaseTerm((IBaseTerm) term));
			} else if (term instanceof IFunctionTerm) {
				networkByEdges.getTerms().put(term.getJdexId(), new FunctionTerm((IFunctionTerm) term));
			}
		}

		for (INamespace ns : requiredINamespaces) {
			networkByEdges.getNamespaces().put(ns.getJdexId(), new Namespace(ns));
		}

		for (ISupport support : requiredISupports) {
			networkByEdges.getSupports().put(support.getJdexId(), new Support(support));
		}

		for (ICitation citation : requiredICitations) {
			networkByEdges.getCitations().put(citation.getJdexId(), new Citation(citation));
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
			if ( term instanceof IBaseTerm) {
				networkByNodes.getTerms().put(term.getJdexId(), new BaseTerm((IBaseTerm) term));
			} else if (term instanceof IFunctionTerm) {
				networkByNodes.getTerms().put(term.getJdexId(), new FunctionTerm((IFunctionTerm) term));
			}
			
		}

		for (INamespace ns : requiredINamespaces) {
			networkByNodes.getNamespaces().put(ns.getJdexId(), new Namespace(ns));
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
		Set<ITerm> nodeTerms = new HashSet<ITerm>();

		for (INode node : nodes) {
			if (node.getRepresents() != null) {
				addTermAndFunctionalDependencies(node.getRepresents(),
						nodeTerms);
			}
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
			if (term instanceof IBaseTerm && ((IBaseTerm) term).getNamespaces() != null) {
			    for (INamespace namespace : ((IBaseTerm)term).getNamespaces())
			        namespaces.add(namespace);
			}
		}
		return namespaces;
	}


}
