package org.ndexbio.common.persistence;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.cxio.aspects.datamodels.EdgeAttributesElement;
import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.aspects.readers.EdgeAttributesFragmentReader;
import org.cxio.aspects.readers.EdgesFragmentReader;
import org.cxio.aspects.readers.NetworkAttributesFragmentReader;
import org.cxio.aspects.readers.NodeAttributesFragmentReader;
import org.cxio.aspects.readers.NodesFragmentReader;
import org.cxio.core.CxElementReader;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.core.interfaces.AspectFragmentReader;
import org.cxio.misc.OpaqueElement;
import org.ndexbio.common.cx.aspect.GeneralAspectFragmentReader;
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

public class CXNetworkSampleGenerator implements AutoCloseable {

	private InputStream inputStream;
	private UUID networkId;
	private Long subNetworkId;
	
	// size of sample is number of edges.
	public static final int sampleSize = 500;
	
	
	public CXNetworkSampleGenerator(UUID networkUUID, Long subNetworkID) throws FileNotFoundException, NdexException {
		this.networkId = networkUUID;
		this.subNetworkId = subNetworkID;
		this.inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + networkId + ".cx");
		
	}
	
	
	private CxElementReader createCXReader () throws IOException {
		HashSet<AspectFragmentReader> readers = new HashSet<>(20);
		
		  readers.add(EdgesFragmentReader.createInstance());
		  readers.add(EdgeAttributesFragmentReader.createInstance());
		  readers.add(NetworkAttributesFragmentReader.createInstance());
		  readers.add(NodesFragmentReader.createInstance());
		  readers.add(NodeAttributesFragmentReader.createInstance());
		  
		  readers.add(new GeneralAspectFragmentReader (NdexNetworkStatus.ASPECT_NAME,
				NdexNetworkStatus.class));
		  readers.add(new GeneralAspectFragmentReader (NamespacesElement.ASPECT_NAME,NamespacesElement.class));
		//  readers.add(new GeneralAspectFragmentReader (FunctionTermElement.ASPECT_NAME,FunctionTermElement.class));
		  readers.add(new GeneralAspectFragmentReader (CitationElement.ASPECT_NAME,CitationElement.class));
		  readers.add(new GeneralAspectFragmentReader (SupportElement.ASPECT_NAME,SupportElement.class));
//		  readers.add(new GeneralAspectFragmentReader (ReifiedEdgeElement.ASPECT_NAME,ReifiedEdgeElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeCitationLinksElement.ASPECT_NAME,EdgeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeSupportLinksElement.ASPECT_NAME,EdgeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeCitationLinksElement.ASPECT_NAME,NodeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeSupportLinksElement.ASPECT_NAME,NodeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (Provenance.ASPECT_NAME,Provenance.class));
		  		  
		  return  CxElementReader.createInstance(inputStream, true,
				   readers);
	}
	
	
	public void createSampleNetwork() throws IOException, NdexException {
		
		CXNetwork result = new CXNetwork();

		CxElementReader cxreader = createCXReader();
		result.setMetadata(cxreader.getPreMetaData());
		
		// first round. Get 500 edges and the node Ids they reference.
		int i = 0;
		Set<Long> nodeIds = new TreeSet<>();
		for ( AspectElement elmt : cxreader ) {
			if (  elmt.getAspectName().equals(EdgesElement.ASPECT_NAME)) {
				// Edge
				EdgesElement e = (EdgesElement) elmt;
				if (subNetworkId ==null || subNetworkId.longValue() == e.getId()  )  {
					result.addEdge(e);
					nodeIds.add(e.getSource());
					nodeIds.add(e.getTarget());
					i++;
				}
			}
			if ( i==sampleSize )
				break;
		} 
		inputStream.close();
		
		this.inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + networkId + ".cx");

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
				for ( Long id : na.getPropertyOf()) {
					if ( nodeIds.contains(id) && 
							(subNetworkId == null || na.getSubnetwork() == null || subNetworkId.equals(na.getSubnetwork()))) {
						result.addNodeAttribute(id, na);
					}
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
				for ( Long id : ea.getPropertyOf()) {
					if ( result.getEdges().containsKey(id) && 
							(subNetworkId == null || ea.getSubnetwork() == null || subNetworkId.equals(ea.getSubnetwork()))) {
						result.addEdgeAttribute(id, ea);
					}
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
		}

		inputStream.close();
		
		//write the sample network out to disk and update the db.
		try (FileOutputStream out = new FileOutputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + networkId + "_sample.cx")) {
			result.write(out);
		}
	}
	
	@Override
	public void close() {
			try {
				inputStream.close();
			} catch (IOException e) {
				System.out.println("failed to colse inputStream: " + e.getMessage());
				e.printStackTrace();
			}
	}

}
