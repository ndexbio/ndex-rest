package org.ndexbio.common.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.ndexbio.common.cx.AspectIterator;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
import org.ndexbio.model.cx.SupportElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.internal.CXNetwork;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CXNetworkSampleGenerator {

//	private InputStream inputStream;
	private UUID networkId;
	private Long subNetworkId;
	private MetaDataCollection srcMetaDataCollection;
	private Long currentTime;
	
	// size of sample is number of edges.
	public static final int sampleSize = 500;
	
	
	public CXNetworkSampleGenerator(UUID networkUUID, Long subNetworkID, MetaDataCollection srcMetaData) {
		this.networkId = networkUUID;
		this.subNetworkId = subNetworkID;
		this.srcMetaDataCollection = srcMetaData;
		this.currentTime = Long.valueOf(Calendar.getInstance().getTimeInMillis());
		
	}
	
	private MetaDataElement getMetaDataElementTempleteFromSrc (String aspectName) {
		MetaDataElement old = this.srcMetaDataCollection.getMetaDataElement(aspectName);
	//	if ( old == null)
	//		throw new NdexException("MetaData " + aspectName + " is missing in network " + this.networkId.toString());
		
		MetaDataElement result = new MetaDataElement();
		result.setConsistencyGroup(old !=null? old.getConsistencyGroup() : Long.valueOf(1l));
		result.setName(aspectName);
		result.setVersion(old!=null? old.getVersion() : "1.0");
		result.setLastUpdate(currentTime);
		return result;
	}
	
	public void createSampleNetwork() throws IOException {
		
		CXNetwork result = new CXNetwork();

		MetaDataCollection metadata = new MetaDataCollection();
		result.setMetadata(metadata);
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/"; 
	
		// if sample is for a subNetwork, get ids of 500 edges from the subNetwork aspect
		Set<Long> edgeIds = null; 
		
		if ( subNetworkId != null) {
			
			edgeIds = new HashSet<>(sampleSize);
			
			try (AspectIterator<SubNetworkElement> subNetIterator = new AspectIterator<>(networkId,SubNetworkElement.ASPECT_NAME , SubNetworkElement.class ) ) {
				while ( subNetIterator.hasNext()) {
					SubNetworkElement subNetwork = subNetIterator.next();
					
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
			
			if (edgeIds.isEmpty()) {  // try the subNetworks aspect to be compatible with the old cyCX spec.
				try (AspectIterator<SubNetworkElement> subNetIterator = new AspectIterator<>(networkId,"subNetworks" , SubNetworkElement.class ) ) {
					while ( subNetIterator.hasNext()) {
						SubNetworkElement subNetwork = subNetIterator.next();
						
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
			
		/*	// add metadata entry
			MetaDataElement mdElmt = getMetaDataElementTempleteFromSrc(SubNetworkElement.ASPECT_NAME);	
			mdElmt.setElementCount(1L);
			metadata.add(mdElmt); */
			
		}
			
		// first round. Get 500 edges and the node Ids they reference.
		int i = 0;
		Set<Long> nodeIds = new TreeSet<>();
		//go through Edge aspect
		Long edgeIdCounter = null;
		try (AspectIterator<EdgesElement> it = new AspectIterator<>(networkId,EdgesElement.ASPECT_NAME , EdgesElement.class )) {

			while (it.hasNext()) {
	        	EdgesElement edge = it.next();
	        	
	        	if ( edgeIds == null ||  edgeIds.contains(edge.getId() )  )  {
					result.addEdge(edge);
					nodeIds.add(edge.getSource());
					nodeIds.add(edge.getTarget());
					if ( edgeIdCounter == null ||edge.getId() > edgeIdCounter.longValue() )
						edgeIdCounter = Long.valueOf(edge.getId());
					i++;
				}
	        	if (i == sampleSize)
	        		break;
	        	
			}
		}
		
		MetaDataElement edgemd = this.getMetaDataElementTempleteFromSrc(EdgesElement.ASPECT_NAME);
		edgemd.setElementCount(Long.valueOf(result.getEdges().size()));
		edgemd.setIdCounter(edgeIdCounter);
		metadata.add(edgemd);
		
		//go through node aspect
		Long nodeIdCounter = null;
		try (FileInputStream inputStream = new FileInputStream(pathPrefix + NodesElement.ASPECT_NAME)) {

			Iterator<NodesElement> it = new ObjectMapper().readerFor(NodesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NodesElement node = it.next();
				
				if (nodeIds.contains(node.getId())) {
					result.addNode(node);
					if ( nodeIdCounter == null ||node.getId() > nodeIdCounter.longValue() )
						nodeIdCounter = Long.valueOf(node.getId());
				}    	
			        	
			}
		}
		
		MetaDataElement nodemd = this.getMetaDataElementTempleteFromSrc(NodesElement.ASPECT_NAME);
		nodemd.setElementCount(Long.valueOf(nodeIds.size()));
		nodemd.setIdCounter(nodeIdCounter);
		metadata.add(nodemd);
		
		//process node attribute aspect
		long nodeAttrCounter = 0;
		java.nio.file.Path nodeAspectFile = Paths.get(pathPrefix + NodeAttributesElement.ASPECT_NAME);
		if ( Files.exists(nodeAspectFile)) { 
			try (FileInputStream inputStream = new FileInputStream(pathPrefix + NodeAttributesElement.ASPECT_NAME)) {

				Iterator<NodeAttributesElement> it = new ObjectMapper().readerFor(NodeAttributesElement.class).readValues(inputStream);

				while (it.hasNext()) {
					NodeAttributesElement na = it.next();
				
					Long id = na.getPropertyOf();
					if ( nodeIds.contains(id) && 
							((subNetworkId == null && na.getSubnetwork() == null) || 
							 (subNetworkId != null && na.getSubnetwork() !=null && subNetworkId.equals(na.getSubnetwork()) )) ) {
						result.addNodeAttribute(id, na);
						nodeAttrCounter ++;
					}
			        	
				}
			}
		}
		
		if ( nodeAttrCounter >0) {
			MetaDataElement nodeAttrmd = this.getMetaDataElementTempleteFromSrc(NodeAttributesElement.ASPECT_NAME);
			nodeAttrmd.setElementCount(Long.valueOf(nodeAttrCounter));
			metadata.add(nodeAttrmd);
		}
		
		//process edge attribute aspect
		long edgeAttrCounter = 0;
		java.nio.file.Path edgeAspectFile = Paths.get(pathPrefix + EdgeAttributesElement.ASPECT_NAME);
		if ( Files.exists(edgeAspectFile)) { 
		  try (FileInputStream inputStream = new FileInputStream(pathPrefix + EdgeAttributesElement.ASPECT_NAME)) {

			Iterator<EdgeAttributesElement> it = new ObjectMapper().readerFor(EdgeAttributesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				EdgeAttributesElement ea = it.next();
				
				Long id = ea.getPropertyOf();
				if ( result.getEdges().containsKey(id) && 
						((subNetworkId == null && ea.getSubnetwork() == null) || 
								 (subNetworkId != null && ea.getSubnetwork() !=null && subNetworkId.equals(ea.getSubnetwork()) ))) {
					result.addEdgeAttribute(id, ea);
					edgeAttrCounter ++;
				}
			        	
			}
		  }
		}

		if ( edgeAttrCounter >0) {
			MetaDataElement edgeAttrmd = this.getMetaDataElementTempleteFromSrc(EdgeAttributesElement.ASPECT_NAME);
			edgeAttrmd.setElementCount(Long.valueOf(edgeAttrCounter));
			metadata.add(edgeAttrmd);
		}
	
		//process network attribute aspect
		long networkAttrCounter = 0;
		java.nio.file.Path networkAttrAspectFile = Paths.get(pathPrefix + NetworkAttributesElement.ASPECT_NAME);
		if ( Files.exists(networkAttrAspectFile)) { 
		  try (FileInputStream inputStream = new FileInputStream(pathPrefix + NetworkAttributesElement.ASPECT_NAME)) {

			Iterator<NetworkAttributesElement> it = new ObjectMapper().readerFor(NetworkAttributesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NetworkAttributesElement nAtt = it.next();
				
				if ((subNetworkId == null && nAtt.getSubnetwork() == null) || 
								 (subNetworkId != null && nAtt.getSubnetwork() !=null && subNetworkId.equals(nAtt.getSubnetwork()) )) {
					result.addNetworkAttribute(nAtt);
					networkAttrCounter ++;
				}
				  	
			}
		  }
		}
		
		if ( networkAttrCounter >0) {
			MetaDataElement netAttrmd = this.getMetaDataElementTempleteFromSrc(NetworkAttributesElement.ASPECT_NAME);
			netAttrmd.setElementCount(Long.valueOf(networkAttrCounter));
			metadata.add(netAttrmd);
		}

		
		//process namespace aspect
		java.nio.file.Path nsAspectFile = Paths.get(pathPrefix + NamespacesElement.ASPECT_NAME);
		if ( Files.exists(nsAspectFile)) { 
		  try (FileInputStream inputStream = new FileInputStream(pathPrefix + NamespacesElement.ASPECT_NAME)) {

			Iterator<NamespacesElement> it = new ObjectMapper().readerFor(NamespacesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				NamespacesElement ns = it.next();
				result.setNamespaces(ns);
			}
		  }
		  
		  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(NamespacesElement.ASPECT_NAME);
		  nsmd.setElementCount(1L);
		  metadata.add(nsmd);
		}

		//process cyVisualProperty aspect
		java.nio.file.Path cyVisPropAspectFile = Paths.get(pathPrefix + CyVisualPropertiesElement.ASPECT_NAME);
		if ( Files.exists(cyVisPropAspectFile)) { 
		  long vpropCount = 0;
		  try (FileInputStream inputStream = new FileInputStream(pathPrefix + CyVisualPropertiesElement.ASPECT_NAME)) {

			Iterator<CyVisualPropertiesElement> it = new ObjectMapper().readerFor(CyVisualPropertiesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				CyVisualPropertiesElement elmt = it.next();
				result.addOpapqueAspect(elmt);
				vpropCount++;
			}
		  }
		  
		  if ( vpropCount > 0) {
			  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(CyVisualPropertiesElement.ASPECT_NAME);
			  nsmd.setElementCount(vpropCount);
			  metadata.add(nsmd);
		  }
		}

		//special case to support old visualProperty aspect
		java.nio.file.Path cyVisPropAspectFileOld = Paths.get(pathPrefix + "visualProperties");
		if ( Files.exists(cyVisPropAspectFileOld)) { 
		  long vpropCount = 0;
		  try (FileInputStream inputStream = new FileInputStream(pathPrefix + "visualProperties")) {

			Iterator<CyVisualPropertiesElement> it = new ObjectMapper().readerFor(CyVisualPropertiesElement.class).readValues(inputStream);

			while (it.hasNext()) {
				CyVisualPropertiesElement elmt = it.next();
				result.addOpapqueAspect(elmt);
				vpropCount++;
			}
		  }
		  
		  if ( vpropCount > 0) {
			  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(CyVisualPropertiesElement.ASPECT_NAME);
			  nsmd.setElementCount(vpropCount);
			  metadata.add(nsmd);
		  }
		}

		
		// process function terms
		long aspElmtCount = 0;
		try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(networkId, FunctionTermElement.ASPECT_NAME, FunctionTermElement.class)) {
			while (it.hasNext()) {
				FunctionTermElement fun = it.next();
				
				if ( nodeIds.contains(fun.getNodeID())) {
					result.addNodeAssociatedAspectElement(fun.getNodeID(), fun);
					aspElmtCount ++;
				}
			}
		}
		
		 if ( aspElmtCount > 0) {
			  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(FunctionTermElement.ASPECT_NAME);
			  nsmd.setElementCount(aspElmtCount);
			  metadata.add(nsmd);
		  }
		
		
		Set<Long> citationIds = new TreeSet<> ();
		
		//process citation links aspects
		aspElmtCount = 0;
		try (AspectIterator<NodeCitationLinksElement> it = new AspectIterator<>(networkId, NodeCitationLinksElement.ASPECT_NAME, NodeCitationLinksElement.class)) {
			while (it.hasNext()) {
				NodeCitationLinksElement cl = it.next();
				
				for ( Long rNodeId : cl.getSourceIds()) {
					if ( nodeIds.contains(rNodeId)) {
						result.addNodeAssociatedAspectElement(rNodeId, 
								 ( cl.getCitationIds().size() ==1? cl : new NodeCitationLinksElement(rNodeId, cl.getCitationIds())) );
						aspElmtCount ++;
						citationIds.addAll(cl.getCitationIds());
					}
				}
			}
		}
		
		if ( aspElmtCount > 0) {
			  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(NodeCitationLinksElement.ASPECT_NAME);
			  nsmd.setElementCount(aspElmtCount);
			  metadata.add(nsmd);
		 }
		
		aspElmtCount = 0;
		try (AspectIterator<EdgeCitationLinksElement> it = new AspectIterator<>(networkId, EdgeCitationLinksElement.ASPECT_NAME, EdgeCitationLinksElement.class)) {
			while (it.hasNext()) {
				EdgeCitationLinksElement cl = it.next();
				
				for ( Long rEdgeId : cl.getSourceIds()) {
					if ( result.getEdges().containsKey(rEdgeId)) {
						result.addEdgeAssociatedAspectElement(rEdgeId, 
								( cl.getCitationIds().size() ==1? cl : new EdgeCitationLinksElement(rEdgeId, cl.getCitationIds())) );
						aspElmtCount++;
						citationIds.addAll(cl.getCitationIds());
					}
				}
			}
		}
		
		if ( aspElmtCount > 0) {
			  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(EdgeCitationLinksElement.ASPECT_NAME);
			  nsmd.setElementCount(aspElmtCount);
			  metadata.add(nsmd);
		 }
		
		if( !citationIds.isEmpty()) {
			try (AspectIterator<CitationElement> it = new AspectIterator<>(networkId, CitationElement.ASPECT_NAME, CitationElement.class)) {
				while (it.hasNext()) {
					CitationElement c = it.next();
					if ( citationIds.contains(c.getId()))
						result.addCitation(c);
				}
			}
			
			MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(CitationElement.ASPECT_NAME);
			  nsmd.setElementCount(Long.valueOf(citationIds.size()));
			  nsmd.setIdCounter(Collections.max(citationIds));
			  metadata.add(nsmd);
		}

		// support and related aspects
		Set<Long> supportIds = new TreeSet<> ();
		
		//process support links aspects
		aspElmtCount = 0;
		try (AspectIterator<NodeSupportLinksElement> it = new AspectIterator<>(networkId, NodeSupportLinksElement.ASPECT_NAME, NodeSupportLinksElement.class)) {
			while (it.hasNext()) {
				NodeSupportLinksElement cl = it.next();
				
				for ( Long rNodeId : cl.getSourceIds()) {
					if ( nodeIds.contains(rNodeId)) {
						result.addNodeAssociatedAspectElement(rNodeId, 
								( cl.getSupportIds().size() ==1? cl : new NodeSupportLinksElement(rNodeId, cl.getSupportIds())) );
						aspElmtCount ++;
						supportIds.addAll(cl.getSupportIds());
					}
				}
			}
		}
		
		if ( aspElmtCount > 0) {
			  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(NodeSupportLinksElement.ASPECT_NAME);
			  nsmd.setElementCount(aspElmtCount);
			  metadata.add(nsmd);
		 }
		
		aspElmtCount = 0;
		try (AspectIterator<EdgeSupportLinksElement> it = new AspectIterator<>(networkId, EdgeSupportLinksElement.ASPECT_NAME, EdgeSupportLinksElement.class)) {
			while (it.hasNext()) {
				EdgeSupportLinksElement cl = it.next();
				
				for ( Long rEdgeId : cl.getSourceIds()) {
					if ( result.getEdges().containsKey(rEdgeId)) {
						result.addEdgeAssociatedAspectElement(rEdgeId, 
								( cl.getSupportIds().size() ==1? cl : new EdgeSupportLinksElement(rEdgeId, cl.getSupportIds())) );
						aspElmtCount++;
						supportIds.addAll(cl.getSupportIds());
					}
				}
			}
		}
		
		if ( aspElmtCount > 0) {
			  MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(EdgeSupportLinksElement.ASPECT_NAME);
			  nsmd.setElementCount(aspElmtCount);
			  metadata.add(nsmd);
		 }
		
		if( !supportIds.isEmpty()) {
			try (AspectIterator<SupportElement> it = new AspectIterator<>(networkId, SupportElement.ASPECT_NAME, SupportElement.class)) {
				while (it.hasNext()) {
					SupportElement e = it.next();
					if ( supportIds.contains(e.getId()))
						result.addSupport(e);
				}
			}
			
			MetaDataElement nsmd = this.getMetaDataElementTempleteFromSrc(SupportElement.ASPECT_NAME);
			  nsmd.setElementCount(Long.valueOf(supportIds.size()));
			  nsmd.setIdCounter(Collections.max(supportIds));
			  metadata.add(nsmd);		
		}

		
		//write the sample network out to disk and update the db.
		try (FileOutputStream out = new FileOutputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/sample.cx")) {
			result.write(out);
		}
	}
	

}
