package org.ndexbio.common.persistence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;
import org.junit.Rule;
import org.junit.Before;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.NdexException;

/**
 * Runs tests using the bypasstest network stored under src/test/resources/bypasstest
 * which is copied to a temporary folder before each test using the
 * setUp() method
 * @author churas
 */
public class TestCXToCX2ServerSideConverterWithByPassTestNetwork {
	
	/**
	 * Temporary folder system setup before each test
	 * and torn down after
	 */
	@Rule
    public TemporaryFolder _tmpFolder = new TemporaryFolder();
	
	/**
	 * Set to temp directory before each test via setUp() method
	 * The data of the network resides in _tmpFolderFile/_networkIdStr/aspects/XXX
	 */
	public File _tmpFolderFile = null;
	
	/**
	 * MetaDataCollection object
	 */
	public MetaDataCollection _mdc;
	
	/**
	 * Network ID String 
	 */
	public final String _networkIdStr = "93FFF875-843C-490A-81B9-5A4282935F9A";

	
	/**
	 * Creates temporary directory _tmpFolderFile and 
	 * copies the bypass network data to _tmpFolderFile/_networkIdStr/aspects/XXX
	 * Finally this loads _mdc with the 'metadata' from bypass
	 * @throws Exception 
	 */
	@Before
	public void setUp() throws Exception {
		_tmpFolderFile = _tmpFolder.newFolder();
		String configFile = _tmpFolderFile.getCanonicalPath() + File.separator + "config";
		TestUtil.writeSimpleConfigToFile(configFile, _tmpFolderFile.getCanonicalPath());
		List<String> aspects = Arrays.asList("cartesianLayout",
										"cyVisualProperties", 
										"edgeAttributes",
										"edges", 
										"networkAttributes",
										"nodeAttributes",
										"nodes",
										"cyHiddenAttributes",
										"cyTableColumn");
		TestUtil.copyNetworkAspects(this.getClass(), "/bypasstest", aspects,
				_tmpFolderFile.getAbsolutePath() + File.separator + _networkIdStr);
		_mdc = TestUtil.getNetworkMetaData(this.getClass(), "/bypasstest/metadata");
	}
	
	@After
	public void tearDown(){
		_tmpFolderFile = null;
	}
	
	@Test
	public void testConvertBypassTest() throws Exception {
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
	
		// run conversion
		List<CxMetadata> metaDataList = converter.convert();
		
		//verify metaDataList returns valid values
		assertEquals(9, metaDataList.size());
	
		for (CxMetadata mData : metaDataList){
			if (mData.getName().equals("networkAttributes") ||
			    mData.getName().equals("visualProperties") ||
				mData.getName().equals("visualEditorProperties") ||
			    mData.getName().equals("attributeDeclarations")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else if (mData.getName().equals("nodes")){
				assertEquals((long)2, (long)mData.getElementCount());
			} else if (mData.getName().equals("edges")){
				assertEquals((long)2, (long)mData.getElementCount());
			} else if (mData.getName().equals("edgeBypasses")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else if (mData.getName().equals("cyHiddenAttributes")){
				assertEquals((long)3, (long)mData.getElementCount());
			} else if (mData.getName().equals("cyTableColumn")){
				assertEquals((long)12, (long)mData.getElementCount());
			} else if (mData.getName().equals("nodeBypasses")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else {
				fail("Unexpected meta data: " + mData.getName());
			}
		}
		
		//verify we got a network.cx2 file
		File cx2File = new File(_tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator
		         + CX2NetworkLoader.cx2NetworkFileName);
		assertTrue(cx2File.isFile());
		assertTrue(cx2File.length() > 0);
		
		//verify aspect files for cx2 are properly created
		File a2Dir = new File(_tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator + CX2NetworkLoader.cx2AspectDirName);
		List<String> cx2AspectFileNames = new ArrayList<>();
		for (String entry : a2Dir.list()){
			cx2AspectFileNames.add(entry);
			File fileCheck = new File(a2Dir.getAbsolutePath() + File.separator + entry);
			assertTrue(entry + " file has 0 size", fileCheck.length() > 0);
		}
		assertEquals(9, cx2AspectFileNames.size());
		assertTrue(cx2AspectFileNames.contains("visualProperties"));
		assertTrue(cx2AspectFileNames.contains("visualEditorProperties"));
		assertTrue(cx2AspectFileNames.contains("nodes"));
		assertTrue(cx2AspectFileNames.contains("edges"));
		assertTrue(cx2AspectFileNames.contains("networkAttributes"));
		assertTrue(cx2AspectFileNames.contains("attributeDeclarations"));
		assertTrue(cx2AspectFileNames.contains("cyTableColumn"));
		assertTrue(cx2AspectFileNames.contains("nodeBypasses"));
		assertTrue(cx2AspectFileNames.contains("edgeBypasses"));

		// @TODO The converter reports duplicate warnings about duplicate network
		//       attribute cause its checked in analyzeAttributes and again in convert()
		List<String> warnings = converter.getWarning();
		assertEquals(0, warnings.size());
	}
	
	@Test
	public void testConvertWithMoreThenTwentyWarnings() throws Exception {

		_mdc.setElementCount("networkAttributes", 22L);
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "networkAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[");
			for (int i = 1 ;i <= 21 ; i++){
				bw.write("{\"n\":\"name\",\"v\":\"" + i + "\"},\n");
			}
			bw.write(" {\"n\":\"name\",\"v\":\"21\"}]\n");
             
			bw.flush();
		}
		
		// run conversion
		converter.convert();
		List<String> warnings = converter.getWarning();
		assertEquals(20, warnings.size());
		assertEquals("CX2-CONVERTER: Duplicated network attribute 'name' found.",
				warnings.get(0));
	}
	
	@Test
	public void testConvertNodeAttributeNumberFormatExceptionAlwaysCreateFalse() throws Exception {
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, false);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "nodeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":64,\"n\":\"node_label_color\",\"v\":\"blue\"}, \n" +
                      " {\"po\":64,\"n\":\"node_size\",\"v\":\"150.0\",\"d\":\"double\"}, \n" +
                      " {\"po\":62,\"n\":\"node_label_color\",\"v\":\"red\"}, \n" +
                      " {\"po\":62,\"n\":\"node_size\",\"v\":\"bad\",\"d\":\"double\"}]");
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
		    fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("For node attribute id: 62 with "
					+ "name 'node_size' received fatal "
					+ "parsing error: Non numeric "
					+ "value 'bad' is declared as "
					+ "type double.", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertNodeAttributeNumberFormatException() throws Exception {
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "nodeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":64,\"n\":\"node_label_color\",\"v\":\"blue\"}, \n" +
                      " {\"po\":64,\"n\":\"node_size\",\"v\":\"150.0\",\"d\":\"double\"}, \n" +
                      " {\"po\":62,\"n\":\"node_label_color\",\"v\":\"red\"}, \n" +
                      " {\"po\":62,\"n\":\"node_size\",\"v\":\"bad\",\"d\":\"double\"}]");
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
		    fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("For node attribute id: 62 with "
					+ "name 'node_size' received fatal "
					+ "parsing error: Non numeric "
					+ "value 'bad' is declared as "
					+ "type double.", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertEdgeAttributeNumberFormatExceptionAlwaysCreateFalse() throws Exception {
		
		_mdc.setElementCount("edgeAttributes", 3L);
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, false);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "edgeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":80,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n" +
					" {\"po\":66,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n" +
					" {\"po\":66,\"n\":\"edge_type\",\"v\":\"wrong\",\"d\": \"integer\"}]");
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
		    fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("For edge attribute id: 66 with "
					+ "name 'edge_type' received fatal "
					+ "parsing error: Non numeric "
					+ "value 'wrong' is declared as "
					+ "type integer.", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertEdgeAttributeNumberFormatException() throws Exception {
		
		_mdc.setElementCount("edgeAttributes", 3L);
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "edgeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":80,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n" +
					" {\"po\":66,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n" +
					" {\"po\":66,\"n\":\"edge_type\",\"v\":\"wrong\",\"d\": \"integer\"}]");
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
		    fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("For edge attribute id: 66 with "
					+ "name 'edge_type' received fatal "
					+ "parsing error: Non numeric "
					+ "value 'wrong' is declared as "
					+ "type integer.", ne.getMessage());
		}
	}
	
	
	@Test
	public void testConvertNetworkAttributesNumberFormatExceptionAlwaysCreateFalse() throws Exception {
		

		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, false);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "networkAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"n\":\"name\",\"v\":\"node_size_ignored\"},\n");
			bw.write(" {\"n\":\"description\",\"v\":\"example\"},\n");
             bw.write(" {\"n\":\"version\",\"v\":\"uhhh\", \"d\": \"integer\"}]");
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
			fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("For network attribute 'version' "
					+ "unable to convert value  to 'integer' "
					+ ": For input string: \"uhhh\"", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertNetworkAttributesNumberFormatException() throws Exception {
		

		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "networkAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"n\":\"name\",\"v\":\"node_size_ignored\"},\n");
			bw.write(" {\"n\":\"description\",\"v\":\"example\"},\n");
             bw.write(" {\"n\":\"version\",\"v\":\"uhhh\", \"d\": \"integer\"}]");
			bw.flush();
		}
		
		// run conversion
		converter.convert();
		List<String> warnings = converter.getWarning();
		assertEquals(1, warnings.size());
		assertEquals("CX2-CONVERTER: For "
				+ "network attribute 'version' "
				+ "unable to convert value  to "
				+ "'integer' : For input string: \"uhhh\"", warnings.get(0));
	}
	
	@Test
	public void testConvertCartesianCoordinateLacksCorrespondingNode() throws Exception {
		
		// update the number of elements in layout cause we will be adding one
		_mdc.setElementCount("cartesianLyaout", 3L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "cartesianLayout";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"node\":64,\"x\":95.0,\"y\":-119.0},\n");
			bw.write(" {\"node\":62,\"x\":-77.0,\"y\":-103.0},\n");
			bw.write(" {\"node\":10,\"x\":1.0,\"y\":2.0}]\n");
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
			fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("Node 10 is referenced in cartesianLayout but not defined in the nodes aspect.", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertNodeAttributeIsNamedNameAlwaysCreateFalse() throws Exception {
		
		// update node attribute count
		_mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, false);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "nodeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":64,\"n\":\"node_label_color\",\"v\":\"blue\"}, \n");
			bw.write(" {\"po\":64,\"n\":\"node_size\",\"v\":\"150.0\",\"d\":\"double\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_label_color\",\"v\":\"red\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_size\",\"v\":\"100.0\",\"d\":\"double\"},\n");
             bw.write(" {\"po\":62,\"n\":\"name\",\"v\":\"uhoh\"}]\n");
			
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
			fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("Node attribute id: 62 "
					+ "is named 'name' which is "
					+ "not allowed in CX spec.", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertNodeAttributeIsNamedRepresentsAlwaysCreateFalse() throws Exception {
		
		//update node attrib count
		_mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, false);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "nodeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":64,\"n\":\"node_label_color\",\"v\":\"blue\"}, \n");
			bw.write(" {\"po\":64,\"n\":\"node_size\",\"v\":\"150.0\",\"d\":\"double\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_label_color\",\"v\":\"red\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_size\",\"v\":\"100.0\",\"d\":\"double\"},\n");
             bw.write(" {\"po\":62,\"n\":\"represents\",\"v\":\"uhoh\"}]\n");
			
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
			fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("Node attribute id: 62 "
					+ "is named 'represents' which is "
					+ "not allowed in CX spec.", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertNodeAttributeIsNamedName() throws Exception {
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = TestUtil.getNetworkMetaData(this.getClass(), "/bypasstest/metadata");
		mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "nodeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":64,\"n\":\"node_label_color\",\"v\":\"blue\"}, \n");
			bw.write(" {\"po\":64,\"n\":\"node_size\",\"v\":\"150.0\",\"d\":\"double\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_label_color\",\"v\":\"red\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_size\",\"v\":\"100.0\",\"d\":\"double\"},\n");
             bw.write(" {\"po\":62,\"n\":\"name\",\"v\":\"uhoh\"}]\n");
			
			bw.flush();
		}
		
		// run conversion
		converter.convert();
		List<String> warnings = converter.getWarning();
		assertEquals(2, warnings.size());
		assertEquals("CX2-CONVERTER: Node attribute id: 62 is named 'name' which is not allowed in CX spec.", warnings.get(0));
		assertEquals("CX2-CONVERTER: Duplicate nodes attribute on id: 62. Attribute 'name' has value (Node 1) and (uhoh)", warnings.get(1));

	}
	
	@Test
	public void testConvertNodeAttributeIsNamedRepresents() throws Exception {
		
		// update node attrib count
		_mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "nodeAttributes";
		File cartFile = new File(cartesianLayoutFile);
		assertTrue(cartFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(cartFile))){
			bw.write("[{\"po\":64,\"n\":\"node_label_color\",\"v\":\"blue\"}, \n");
			bw.write(" {\"po\":64,\"n\":\"node_size\",\"v\":\"150.0\",\"d\":\"double\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_label_color\",\"v\":\"red\"}, \n");
			bw.write(" {\"po\":62,\"n\":\"node_size\",\"v\":\"100.0\",\"d\":\"double\"},\n");
             bw.write(" {\"po\":62,\"n\":\"represents\",\"v\":\"uhoh\"}]\n");
			
			bw.flush();
		}
		
		// run conversion
		converter.convert();
		List<String> warnings = converter.getWarning();
		assertEquals(1, warnings.size());
		assertEquals("CX2-CONVERTER: Node attribute id: 62 is named 'represents' which is not allowed in CX spec.", warnings.get(0));

	}
	
	@Test
	public void testConvertEdgeAttributeIsNamedInteractionAlwaysCreateFalse() throws Exception {
		
		// update edge attr count
		_mdc.setElementCount("edgeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, false);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "edgeAttributes";
		File edgeAttrFile = new File(cartesianLayoutFile);
		assertTrue(edgeAttrFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(edgeAttrFile))){
			bw.write("[{\"po\":80,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n");
			bw.write(" {\"po\":80,\"n\":\"edge_type\",\"v\":\"2.5\"}, \n");
			bw.write(" {\"po\":66,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n");
			bw.write(" {\"po\":66,\"n\":\"edge_type\",\"v\":\"0.5\"},\n");
			bw.write(" {\"po\":66,\"n\":\"interaction\",\"v\":\"wrong\"}]");
			bw.flush();
		}
		
		// run conversion
		try {
			converter.convert();
			fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("Edge attribute id: 66 "
					+ "is named 'interaction' which is "
					+ "not allowed in CX spec.", ne.getMessage());
		}
	}
	
	@Test
	public void testConvertEdgeAttributeIsNamedInteraction() throws Exception {
		
		// update edge attr count
		_mdc.setElementCount("edgeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(_tmpFolderFile.getAbsolutePath() + File.separator, 
				_mdc, _networkIdStr, null, true);
		
		String cartesianLayoutFile = _tmpFolderFile.getAbsolutePath()
				+ File.separator + _networkIdStr + File.separator 
				+ CXNetworkLoader.CX1AspectDir + File.separator + "edgeAttributes";
		File edgeAttrFile = new File(cartesianLayoutFile);
		assertTrue(edgeAttrFile.delete());

		// writing out new cartesianLayout aspect with extra node coordinate
		// that does not match any of the nodes in this network
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(edgeAttrFile))){
			bw.write("[{\"po\":80,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n");
			bw.write(" {\"po\":80,\"n\":\"edge_type\",\"v\":\"2.5\"}, \n");
			bw.write(" {\"po\":66,\"n\":\"name\",\"v\":\"Node 1 (interacts with) Node 2\"},\n");
			bw.write(" {\"po\":66,\"n\":\"edge_type\",\"v\":\"0.5\"},\n");
			bw.write(" {\"po\":66,\"n\":\"interaction\",\"v\":\"wrong\"}]");
			bw.flush();
		}
		
		// run conversion
		converter.convert();
		List<String> warnings = converter.getWarning();
		assertEquals(2, warnings.size());
		assertEquals("CX2-CONVERTER: Edge attribute id: 66 is named 'interaction' which is not allowed in CX spec.", warnings.get(0));
		assertEquals("CX2-CONVERTER: Duplicate edges attribute on id: 66. Attribute 'interaction' has value (wrong) and (interacts with)", warnings.get(1));
	}
}
