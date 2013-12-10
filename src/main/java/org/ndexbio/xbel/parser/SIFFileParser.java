package org.ndexbio.xbel.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.ndexbio.orientdb.service.OrientdbNetworkFactory;
import org.ndexbio.orientdb.service.SIFNetworkService;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.xbel.splitter.HeaderSplitter;
import org.ndexbio.xbel.splitter.NamespaceGroupSplitter;
import org.ndexbio.xbel.splitter.StatementGroupSplitter;
import org.xml.sax.XMLReader;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

/*
 * Lines in the SIF file specify a source node, a relationship type
 * (or edge type), and one or more target nodes.
 * 
 * see: http://wiki.cytoscape.org/Cytoscape_User_Manual/Network_Formats
 */
public class SIFFileParser {

	private final File sifFile;
	private final String sifURI;
	//private final ValidationState validationState;
	private final List<String> msgBuffer;
	
	private INetwork network;

	public SIFFileParser(String fn) throws Exception {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(fn),
				"A filename is required");
		this.msgBuffer = Lists.newArrayList();
		this.sifFile = new File(fn);
		this.sifURI = sifFile.toURI().toString();
		//this.validationState = new SIFFileValidator(fn).getValidationState();
		//this.msgBuffer.add(this.validationState.getValidationMessage());
	}

	/*
	 * Whitespace (space or tab) is used to delimit the names 
	 * in the simple interaction file format. However, in some 
	 * cases spaces are desired in a node name or edge type. 
	 * The standard is that, if the file contains any tab characters, 
	 * then tabs are used to delimit the fields and spaces are 
	 * considered part of the name. If the file contains no tabs, 
	 * then any spaces are delimiters that separate names 
	 * (and names cannot contain spaces).
	 */
	public void parseSIFFile() {
		try {
			this.getMsgBuffer().add("Parsing lines from " + this.getSIFURI());
			BufferedReader bufferedReader;
			try {
				bufferedReader = new BufferedReader(new FileReader(this.getSifFile()));
			} catch (FileNotFoundException e1) {
				this.getMsgBuffer().add("Could not read " + this.getSIFURI());
				//e1.printStackTrace();
				return;
			}
			// scan for tabs
			boolean tabDelimited = scanForTabs(this.getSIFURI());
			this.createNetwork();
			this.processRows(tabDelimited, bufferedReader);
			// persist the network domain model, commit the transaction, close database connection
			SIFNetworkService.getInstance().persistNewNetwork();
		} catch (Exception e) {
			// rollback current transaction and close the database connection
			SIFNetworkService.getInstance().rollbackCurrentTransaction();
			e.printStackTrace();
		} 		
	}
	
	private boolean scanForTabs(String sifuri2) {
		// TODO Auto-generated method stub
		return false;
	}

	private void createNetwork() throws Exception {
		String networkTitle = this.sifFile.getName();
		this.network = OrientdbNetworkFactory.INSTANCE.createTestNetwork(networkTitle);	
		this.getMsgBuffer().add("New SIF: " +network.getTitle());
	}

	private void processRows(boolean tabDelimited, BufferedReader br) {
		
		try {
			
			String line;
			while ((line = br.readLine()) != null) {
				String[] tokens = null;
			   if (tabDelimited){
				   tokens = line.split("\t");
			   } else {
				   tokens = line.split("\\s+");
			   }

			   if (tokens.length == 1) addNode(tokens[0]);
			   if (tokens.length == 3) addEdge(tokens[0], tokens[1], tokens[2]);
			   // TODO: handle case of multiple object nodes
			}
			br.close();
		} catch (IOException e) {
			this.getMsgBuffer().add(e.getMessage());
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			this.getMsgBuffer().add(e.getMessage());
		}
	}
	
	private INode addNode(String name) throws ExecutionException{
		IBaseTerm term = SIFNetworkService.getInstance().findOrCreateNodeBaseTerm(name);
		if (null != term){
			INode node = SIFNetworkService.getInstance().findOrCreateINode(term);
			return node;
		}
		return null;
		
	}
	
	private void addEdge(String subject, String predicate, String object) throws ExecutionException{
		INode subjectNode = addNode(subject);
		INode objectNode = addNode(object);
		IBaseTerm predicateTerm = SIFNetworkService.getInstance().findOrCreatePredicate(predicate);
		SIFNetworkService.getInstance().createIEdge(subjectNode, objectNode, predicateTerm);
		
	}

/*
	public ValidationState getValidationState() {
		return this.validationState;
	}
*/
	public List<String> getMsgBuffer() {
		return this.msgBuffer;
	}

	public String getSIFURI() {
		return sifURI;
	}

	public File getSifFile() {
		return sifFile;
	}
	
	

}
