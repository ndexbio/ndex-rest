package org.ndexbio.rest.services.v3;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cx2.io.CX2AspectWriter;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TSVWriterThread extends Thread {
	
	private OutputStreamWriter out;
	private UUID networkId;
	private boolean exportNodes;
	private static final String delimiter = "\t";
	private String listDelimiter;
	private String nodeKey;
	private boolean includeHeader;
	private String[] nodeAttrs;
	private String[] edgeAttrs;
	//private boolean includeCoordinates;
	private CxAttributeDeclaration attrDecls;
	
	private static final String srcPrefix = "source_";
	private static final String tgtPrefix = "target_";
	
	private static final String idStr = "id";
	
	private boolean quoteListString = false;
	
	
	public TSVWriterThread ( OutputStream out, UUID networkId, CxAttributeDeclaration attrDecls, String type, boolean includeHeader, 
		    String listDelimiter, String nodeKey, 
			String[] nodeAttrs, String[] edgeAttrs, boolean quoteStringInList) throws NdexException {
		this.out = new OutputStreamWriter(out);
		this.networkId = networkId;
		this.exportNodes = type.equals("node");
		this.includeHeader = includeHeader;
		this.listDelimiter = listDelimiter;
		this.nodeKey = nodeKey;
		this.nodeAttrs = nodeAttrs;
		this.edgeAttrs = edgeAttrs;
		this.attrDecls = attrDecls;
		this.quoteListString = quoteStringInList;
		
		if ( !nodeKey.equals(idStr)) {
			Map<String, DeclarationEntry> a = attrDecls.getDeclarations().get(CxNode.ASPECT_NAME);
			if ( a == null || a.get(nodeKey) == null )
				throw new NdexException ("Node attribute " + nodeKey + " doesn't exist in this network.");
			ATTRIBUTE_DATA_TYPE t = a.get(nodeKey).getDataType();
			if ( t != ATTRIBUTE_DATA_TYPE.STRING && 
					t != ATTRIBUTE_DATA_TYPE.LONG && t != ATTRIBUTE_DATA_TYPE.INTEGER) {
				throw new NdexException ("Node attribute " + nodeKey + " can't be used as a key. Its type has to be string, long, or integer.");
				
			}
		}
	}
	
	
	@Override
	public void run() {
		if (exportNodes ) 			
			writeNodes();
		else		
			writeEdges();
	}
	
	
	private void writeNodes() {

		try (FileInputStream in = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId 
				+ "/" + CX2NetworkLoader.cx2AspectDirName + "/" + CxNode.ASPECT_NAME) ) {

			ObjectMapper om = new ObjectMapper();
			Map<String, DeclarationEntry > attrTable = attrDecls == null? 
					        null: attrDecls.getDeclarations().get(CxNode.ASPECT_NAME);
			if ( nodeAttrs == null) {
				if (attrTable != null) {
					nodeAttrs = new String[attrTable.size()];
					int i = 0;
					for ( String s : attrTable.keySet()) {
						nodeAttrs[i++]=s;
					}	
				}
			}
			
			if ( includeHeader) {
				writeNodeTSVHeader(attrTable);
			}
			try (MappingIterator<CxNode> it = om.readerFor(CxNode.class).readValues(in) ) {
				while ( it.hasNext()) {
					CxNode node = it.next();
					node.extendToFullNode(attrTable);
                    out.write(node.getId().toString());
                    if ( nodeAttrs !=null && nodeAttrs.length > 0) {
                    	for ( String attrName : nodeAttrs) {
                    		out.write(delimiter);
                    		Object v = node.getAttributes().get(attrName);
                    		ATTRIBUTE_DATA_TYPE t = attrTable.get(attrName).getDataType();
                    		out.write(cvtValueToString(v, t,true));
                    	}                    	
                    }
                    out.write("\n");
				}

			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				out.close();
			} catch (IOException e) {
			}
		}

	}
	
	
	private void writeEdges() {

		ObjectMapper om = new ObjectMapper();
		Map<String, DeclarationEntry > edgeAttrTable = null;
		if ( edgeAttrs == null) {
			edgeAttrTable= attrDecls.getDeclarations().get(CxEdge.ASPECT_NAME);
			if (edgeAttrTable != null) {
				edgeAttrs = new String[edgeAttrTable.size()];
				int i = 0;
				for ( String s : edgeAttrTable.keySet()) {
					edgeAttrs[i++]=s;
				}	
			}
			//move interaction to the front if it exists.
			if ( edgeAttrs != null) {
			for ( int i = 0; i < edgeAttrs.length; i++) {
				if ( edgeAttrs[i].equals(CxEdge.INTERACTION)) {
					if(i!=0) {
						String s = edgeAttrs[0];
						edgeAttrs[0] = edgeAttrs[i];
						edgeAttrs[i] = s;
					}
				}
			}
			}
		}
		
		Map<String, DeclarationEntry> nodeAttrTable = attrDecls == null? null: 
			attrDecls.getDeclarations().get(CxNode.ASPECT_NAME);

		Map<Long,CxNode> nodeTable = new TreeMap<>();
		
		try (FileInputStream in = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId 
				+ "/" + CX2NetworkLoader.cx2AspectDirName + "/" + CxNode.ASPECT_NAME) ) {

			try (MappingIterator<CxNode> it = om.readerFor(CxNode.class).readValues(in) ) {
				while ( it.hasNext()) {
					CxNode node = it.next();
					node.extendToFullNode(attrDecls.getDeclarations().get(CxNode.ASPECT_NAME));
					nodeTable.put(node.getId(), node);
				}

			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		} 
		
		
		try (FileInputStream in = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId 
				+ "/" + CX2NetworkLoader.cx2AspectDirName + "/" + CxEdge.ASPECT_NAME) ) {

			if ( includeHeader) {
				writeEdgeTSVHeader(edgeAttrTable);
			}
			
			ATTRIBUTE_DATA_TYPE nodeKeyType = nodeKey.equals(idStr)? 
					ATTRIBUTE_DATA_TYPE.LONG : nodeAttrTable.get(nodeKey).getDataType();
			try (MappingIterator<CxEdge> it = om.readerFor(CxEdge.class).readValues(in) ) {
				while ( it.hasNext()) {
					CxEdge edge = it.next();
					edge.extendToFullNode(edgeAttrTable);
                    out.write(edge.getId().toString() + delimiter);
                    
                    CxNode srcNode = nodeTable.get(edge.getSource());
                    CxNode tgtNode = nodeTable.get(edge.getTarget());
                    
                    if (nodeKey.equals(idStr)) {
                    	out.write(srcNode.getId() + delimiter + tgtNode.getId());
                    } else {
                    	out.write ( cvtValueToString(srcNode.getAttributes().get(nodeKey) , nodeKeyType,true) + 
                    			delimiter + cvtValueToString(tgtNode.getAttributes().get(nodeKey) , nodeKeyType,true));
                    }
                    
                    if ( edgeAttrs !=null && edgeAttrs.length > 0) {
                    	for ( String attrName : edgeAttrs) {
                    		out.write(delimiter);
                    		Object v = edge.getAttributes().get(attrName);
                    		ATTRIBUTE_DATA_TYPE t = edgeAttrTable.get(attrName).getDataType();
                    		out.write(cvtValueToString(v, t,true));
                    	}                    	
                    }
                    
                    if ( nodeAttrs !=null && nodeAttrs.length > 0 ) {
                    	for ( String attrName : nodeAttrs) {
                    		out.write(delimiter);
                    		Object v = srcNode.getAttributes().get(attrName);
                    		ATTRIBUTE_DATA_TYPE t = nodeAttrTable.get(attrName).getDataType();
                    		out.write(cvtValueToString(v, t,true));
                    	}
                    	
                    	for ( String attrName : nodeAttrs) {
                    		out.write(delimiter);
                    		Object v = tgtNode.getAttributes().get(attrName);
                    		ATTRIBUTE_DATA_TYPE t = nodeAttrTable.get(attrName).getDataType();
                    		out.write(cvtValueToString(v, t,true));
                    	}
                    	
                    }
                    
                    out.write("\n");
				}

			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				out.close();
			} catch (IOException e) {
			}
		}		
		
	}
	
	
	private void writeNodeTSVHeader( Map<String,DeclarationEntry> nodeAttrDecls) throws IOException {
		out.write(idStr);
		
		int i = 0;
		if (nodeAttrs != null && nodeAttrs.length > 0) {
			for ( String attrName : nodeAttrs) {
				out.write(delimiter);
				if ( attrName.equals(idStr)) {
					while ( nodeAttrDecls.containsKey(attrName + " ("+ i + ")")) {
						i ++;
					}
					out.write( attrName + " ("+ i + ")");
				} else {
					out.write(attrName.replace('\t', ' '));
				}	
			}
		}
		
		out.write("\n");
		
	}

	private void writeEdgeTSVHeader( Map<String,DeclarationEntry> edgeAttrDecls) throws IOException {
		
		out.write(idStr + delimiter + srcPrefix + nodeKey.replace('\t', ' ')+ delimiter + tgtPrefix + 
				nodeKey.replace('\t', ' '));
	
		int i = 0;
		if (edgeAttrs != null && edgeAttrs.length > 0) {
			for ( String attrName : edgeAttrs) {
				out.write(delimiter);
				if ( attrName.equals(idStr)) {
					while ( edgeAttrDecls.containsKey(attrName + " ("+ i + ")")) {
						i ++;
					}
					out.write( attrName + " ("+ i + ")");
				} else
					out.write(attrName.replace('\t', ' '));
			}
		}
		
		if ( nodeAttrs !=null && nodeAttrs.length > 0 ) {
			for ( String attrName : nodeAttrs) {
				out.write(delimiter + srcPrefix + attrName.replace('\t', ' '));
			}
			for ( String attrName : nodeAttrs) {
				out.write(delimiter + tgtPrefix + attrName.replace('\t', ' '));
			}
		}
		
		out.write("\n");
	}
	
	private String cvtValueToString(Object v, ATTRIBUTE_DATA_TYPE dType, boolean quoteString) {
		if (v == null)
			return "";
		if (v instanceof String)
			return quoteString ? "\"" + ((String)v).replaceAll("\"","\"\"") + "\"" :
				    (String)v;
		if ( dType.isSingleValueType())
			return v.toString();
		
		List<Object> lv = (List<Object>)v;
		lv = lv.stream().map( e -> {return cvtValueToString(e, dType.elementType(), quoteListString);} ).collect(Collectors.toList());
		return "\"" + StringUtils.join(lv, listDelimiter).replaceAll("\"","\"\"") +"\"";
	}
	

}
