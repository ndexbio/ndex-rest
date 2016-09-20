package org.ndexbio.common.persistence;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.cxio.aspects.datamodels.EdgeAttributesElement;
import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.aspects.datamodels.SubNetworkElement;
import org.cxio.aspects.readers.EdgeAttributesFragmentReader;
import org.cxio.aspects.readers.EdgesFragmentReader;
import org.cxio.aspects.readers.NetworkAttributesFragmentReader;
import org.cxio.aspects.readers.NodeAttributesFragmentReader;
import org.cxio.aspects.readers.NodesFragmentReader;
import org.cxio.core.CxElementReader;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.core.interfaces.AspectFragmentReader;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.misc.OpaqueElement;
import org.ndexbio.common.cx.GeneralAspectFragmentReader;
import org.ndexbio.common.solr.NodeIndexEntry;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
import org.ndexbio.model.cx.Provenance;
import org.ndexbio.model.cx.SupportElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.internal.CXNetwork;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CXNetworkSampleGenerator {

//	private InputStream inputStream;
	private UUID networkId;
	private Long subNetworkId;
	
	// size of sample is number of edges.
	public static final int sampleSize = 500;
	
	
	public CXNetworkSampleGenerator(UUID networkUUID, Long subNetworkID) {
		this.networkId = networkUUID;
		this.subNetworkId = subNetworkID;
		
	}
	
	public void createSampleNetwork() throws IOException, NdexException {
		
		CXNetwork result = new CXNetwork();

		MetaDataCollection metadata = new MetaDataCollection();
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/"; 
	
		// if sample is for a subNetwork, get ids of 500 edges from the subNetwork aspect
		List<Long> edgeIds = null; 

		if ( subNetworkId != null) {
			
			edgeIds = new ArrayList<>(sampleSize);
			
			try (FileInputStream inputStream = new FileInputStream(pathPrefix +  SubNetworkElement.ASPECT_NAME)) {

				Iterator<SubNetworkElement> it = new ObjectMapper().readerFor(SubNetworkElement.class).readValues(inputStream);

				while (it.hasNext()) {
					SubNetworkElement subNetwork = it.next();
	        	
					if (subNetworkId.equals(subNetwork.getId())  )  {
						int i = 0;
						for (Long edgeId : subNetwork.getEdges() ) {
							edgeIds.add(edgeId);
							i++; 
							if ( i >= sampleSize) break;
	        			
						}					
					}
	        	
				}
			}
		}
			
		// first round. Get 500 edges and the node Ids they reference.
		int i = 0;
		Set<Long> nodeIds = new TreeSet<>();
		//go through Edge aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + EdgesElement.ASPECT_NAME)) {

			Iterator<EdgesElement> it = new ObjectMapper().readerFor(EdgesElement.class).readValues(inputStream);

			while (it.hasNext()) {
	        	EdgesElement edge = it.next();
	        	
	        	if ( edgeIds == null || (edgeIds !=null && edgeIds.contains(edge.getId()) )  )  {
					result.addEdge(edge);
					nodeIds.add(edge.getSource());
					nodeIds.add(edge.getTarget());
					i++;
				}
	        	if (i == sampleSize)
	        		break;
	        	
			}
		}
		
		//go through node aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + NodesElement.ASPECT_NAME)) {

			Iterator<NodesElement> it = new ObjectMapper().readerFor(NodesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NodesElement node = it.next();
				
				if (nodeIds.contains(node.getId())) {
					result.addNode(node);
				//	nodeIds.remove(node.getId());
				}    	
			        	
			}
		}
		
		//process node attribute aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + NodeAttributesElement.ASPECT_NAME)) {

			Iterator<NodeAttributesElement> it = new ObjectMapper().readerFor(NodeAttributesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NodeAttributesElement na = it.next();
				
				Long id = na.getPropertyOf();
				if ( nodeIds.contains(id) && 
						(subNetworkId == null || na.getSubnetwork() == null || subNetworkId.equals(na.getSubnetwork()))) {
					result.addNodeAttribute(id, na);
				}
			        	
			}
		}
		
		
		//process edge attribute aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + EdgeAttributesElement.ASPECT_NAME)) {

			Iterator<EdgeAttributesElement> it = new ObjectMapper().readerFor(EdgeAttributesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				EdgeAttributesElement na = it.next();
				
				Long id = na.getPropertyOf();
				if ( nodeIds.contains(id) && 
						(subNetworkId == null || na.getSubnetwork() == null || subNetworkId.equals(na.getSubnetwork()))) {
					result.addEdgeAttribute(id, na);
				}
			        	
			}
		}

	
		//process network attribute aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + NetworkAttributesElement.ASPECT_NAME)) {

			Iterator<NetworkAttributesElement> it = new ObjectMapper().readerFor(NetworkAttributesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NetworkAttributesElement nAtt = it.next();
				
				if ( subNetworkId == null || nAtt.getSubnetwork() == null || subNetworkId.equals(nAtt.getSubnetwork())) {
					result.addNetworkAttribute(nAtt);
				}
				  	
			}
		}

		
		//process namespace aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + NamespacesElement.ASPECT_NAME)) {

			Iterator<NamespacesElement> it = new ObjectMapper().readerFor(NamespacesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NamespacesElement ns = it.next();
				result.setNamespaces(ns);
			}
		}

		//process namespace aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + NamespacesElement.ASPECT_NAME)) {

			Iterator<NamespacesElement> it = new ObjectMapper().readerFor(NamespacesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NamespacesElement ns = it.next();
				result.setNamespaces(ns);
			}
		}

		//process namespace aspect
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + NamespacesElement.ASPECT_NAME)) {

			Iterator<NamespacesElement> it = new ObjectMapper().readerFor(NamespacesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NamespacesElement ns = it.next();
				result.setNamespaces(ns);
			}
		}
		
	/*	this.inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + networkId + ".cx");

		// now pull out all the relevent aspect elements in the second round.
		cxreader = createCXReader();
		
		for ( AspectElement elmt : cxreader ) {
			switch ( elmt.getAspectName() ) {
			case NodesElement.ASPECT_NAME :       //Node
				NodesElement n = (NodesElement) elmt;
				if (nodeIds.contains(n.getId()))
					result.addNode(n);
				break;
			case NodeAttributesElement.ASPECT_NAME:  // node attributes
				NodeAttributesElement na = (NodeAttributesElement) elmt;
				Long id = na.getPropertyOf();
					if ( nodeIds.contains(id) && 
							(subNetworkId == null || na.getSubnetwork() == null || subNetworkId.equals(na.getSubnetwork()))) {
						result.addNodeAttribute(id, na);
					}
				
				break;
			case NetworkAttributesElement.ASPECT_NAME: //network attributes
				NetworkAttributesElement nAtt = (NetworkAttributesElement) elmt;
				if ( subNetworkId == null || nAtt.getSubnetwork() == null || subNetworkId.equals(nAtt.getSubnetwork())) {
					result.addNetworkAttribute(nAtt);
				}
				break;
			case EdgeAttributesElement.ASPECT_NAME : // edge attributes
				EdgeAttributesElement ea = (EdgeAttributesElement) elmt;
				Long eid = ea.getPropertyOf();
				if ( result.getEdges().containsKey(eid) && 
							(subNetworkId == null || ea.getSubnetwork() == null || subNetworkId.equals(ea.getSubnetwork()))) {
						result.addEdgeAttribute(eid, ea);
				}
				
				break;
			case NamespacesElement.ASPECT_NAME:
				NamespacesElement ns = (NamespacesElement)elmt;
				result.setNamespaces(ns);
				break;
			case CitationElement.ASPECT_NAME:
				break;
			case SupportElement.ASPECT_NAME:
				break;
			case NodeCitationLinksElement.ASPECT_NAME:
				break;
			case NodeSupportLinksElement.ASPECT_NAME:
				break;
			case EdgeCitationLinksElement.ASPECT_NAME:
				break;
			case EdgeSupportLinksElement.ASPECT_NAME:
				break;
			default:    // opaque aspect
				if ( elmt.getAspectName().equals(CyVisualPropertiesElement.ASPECT_NAME) 
						|| elmt.getAspectName().equals("visualProperties")) {
					 OpaqueElement e = (OpaqueElement) elmt;
					 result.addOpapqueAspect(e);
				} 
			}
		} */
		
		//write the sample network out to disk and update the db.
		try (FileOutputStream out = new FileOutputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + networkId + "_sample.cx")) {
			result.write(out);
		}
	}
	

}
