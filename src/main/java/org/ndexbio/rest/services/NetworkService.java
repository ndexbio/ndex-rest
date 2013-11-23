package org.ndexbio.rest.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.ndexbio.rest.domain.IGroup;
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
import org.ndexbio.rest.models.Group;
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
	protected final Map<String, VertexFrame> networkIndex = Maps.newHashMap();

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

		// Create the network object in the database managed by an iNetwork
		final INetwork iNetwork = _orientDbGraph.addVertex("class:network",
				INetwork.class);
		// Assert ownership
		networkOwner.addOwnedNetwork(iNetwork);

		// Create a map to associate objects created with the indices they had
		// in the network model
		final Map<String, VertexFrame> networkIndex = Maps.newHashMap();
		try {
			// First create all namespaces used by the network
			createNamespaces(iNetwork, network);
			// Then create terms which may reference the namespaces
			// The terms are created in order of reference - later terms may
			// refer to earlier terms
			createTerms(iNetwork, network);
			// Then create nodes that may reference terms
			createNodes(iNetwork, network);
			// Then create edges that reference terms nodes
			createEdges(iNetwork, network);
			// Then create citations that reference edges
			createCitations(iNetwork, network);
			// Then create supports that reference edges and citations
			createSupports(iNetwork, network);			
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
	private void createNamespaces(INetwork iNetwork, Network network) {
		List<Namespace> namespaceList = network.getNamespaces();
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
			this.networkIndex.put(namespace.getJdexId(), ins);
		}

	}

	/*
	 * map terms in network model object to terms in the network domain object
	 * 
	 * Note that this requires that the list of terms is ordered such that terms
	 * may only refer to other terms if those terms come earlier in the list.
	 */
	private void createTerms(INetwork iNetwork, Network network) {
		List<Term> termList = network.getTerms();
		for (Term term : termList) {

			if (term instanceof BaseTerm) {
				final IBaseTerm iBaseTerm = _orientDbGraph.addVertex(
						"class:baseTerm", IBaseTerm.class);
				iBaseTerm.setName(((BaseTerm) term).getName());
				iBaseTerm.setJdexId(term.getJdexId());
				Namespace ns = ((BaseTerm) term).getNamespace();
				if (ns != null) {
					INamespace ins = (INamespace) networkIndex.get(ns
							.getJdexId());
					iBaseTerm.setNamespace(ins);
					// TODO check for case where no ins found in networkIndex
				}
				iNetwork.addTerm(iBaseTerm);
				networkIndex.put(iBaseTerm.getJdexId(), iBaseTerm);

			} else if (term instanceof FunctionTerm) {
				FunctionTerm fterm = (FunctionTerm)term;
				final IFunctionTerm iFunctionTerm = _orientDbGraph.addVertex(
						"class:functionTerm", IFunctionTerm.class);
				iFunctionTerm.setJdexId(fterm.getJdexId());
				IBaseTerm function = (IBaseTerm) networkIndex
						.get(fterm.getTermFunction()
								.getJdexId());
				iFunctionTerm.setTermFunction(function);
				// TODO check for case where no function found in networkIndex
				
				for (Map.Entry entry : fterm.getTextParameters().entrySet()){
					Integer index = (Integer) entry.getKey();
					String param = (String) entry.getValue();
					iFunctionTerm.getTextParameters().put(index,param);
				}

				for (Map.Entry entry : fterm.getTermParameters().entrySet()){
					Integer index = (Integer) entry.getKey();
					String termJDExId = (String) entry.getValue();
					ITerm parameterTerm = (ITerm) networkIndex.get(termJDExId);
					iFunctionTerm.getTermParameters().put(index,parameterTerm);
				}

				iNetwork.addTerm(iFunctionTerm);
				networkIndex.put(iFunctionTerm.getJdexId(), iFunctionTerm);

			} else
				continue;

		}

	}

	private void createNodes(INetwork iNetwork, Network network) {
		Integer nodesCount = 0;
		for (Node node : network.getNdexNodes()){
			INode iNode = _orientDbGraph.addVertex("class:node", INode.class);
			iNode.setJdexId(node.getJdexId());
			nodesCount++;
			Term term = node.getRepresents();
			ITerm representedITerm = (ITerm) networkIndex.get(term.getJdexId());
			iNode.setRepresents(representedITerm);	
			iNetwork.addNdexNode(iNode);
			networkIndex.put(iNode.getJdexId(), iNode);
		}
		iNetwork.setNdexNodeCount(nodesCount);
	}

	private void createEdges(INetwork iNetwork, Network network) {
		Integer edgesCount = 0;
		for (Edge edge : network.getNdexEdges()){
			IEdge iEdge = _orientDbGraph.addVertex("class:edge", IEdge.class);
			iEdge.setJdexId(edge.getJdexId());
			edgesCount++;
			
			Node subject = edge.getSubject();
			INode subjectINode = (INode) networkIndex.get(subject.getJdexId());
			iEdge.setSubject(subjectINode);
			
			BaseTerm term = edge.getPredicate();
			IBaseTerm predicateITerm = (IBaseTerm) networkIndex.get(term.getJdexId());
			iEdge.setPredicate(predicateITerm);
			
			Node object = edge.getObject();
			INode objectINode = (INode) networkIndex.get(object.getJdexId());
			iEdge.setObject(objectINode);
			
			iNetwork.addNdexEdge(iEdge);
			networkIndex.put(iEdge.getJdexId(), iEdge);
		}
		iNetwork.setNdexEdgeCount(edgesCount);

	}

	private void createCitations(INetwork iNetwork, Network network) {
		for (Citation citation : network.getCitations()){
			ICitation iCitation = _orientDbGraph.addVertex("class:citation", ICitation.class);
			iCitation.setJdexId(citation.getJdexId());
			iCitation.setTitle(citation.getTitle());
			iCitation.setIdentifier(citation.getIdentifier());
			iCitation.setType(citation.getType());
			iCitation.setContributors(citation.getContributors());
			
			for (String edgeId : citation.getEdges()){
				IEdge citationEdge = (IEdge) networkIndex.get(edgeId);
				iCitation.addNdexEdge(citationEdge);
			}	
			iNetwork.addCitation(iCitation);
			networkIndex.put(iCitation.getJdexId(), iCitation);
		}
		
	}
	
	private void createSupports(INetwork iNetwork, Network network) {
		for (Support support : network.getSupports()){
			ISupport iSupport = _orientDbGraph.addVertex("class:support", ISupport.class);
			iSupport.setJdexId(support.getJdexId());
			iSupport.setText(support.getText());
			
			for (String edgeId : support.getEdges()){
				IEdge supportEdge = (IEdge) networkIndex.get(edgeId);
				iSupport.addNdexEdge(supportEdge);
			}	
			
			if (support.getCitation() != null){
				ICitation citation = (ICitation) networkIndex.get(support.getCitation().getJdexId());
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

		final String query = "select from xNetwork " + where_clause
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
			network.setProperties(updatedNetwork.getProperties());
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
}
