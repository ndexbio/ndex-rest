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
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.converter.AspectAttributeStat;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.NdexException;

/**
 *
 * @author churas
 */
public class TestCXToCX2ServerSideConverter {
	
	@Rule
    public TemporaryFolder _tmpFolder = new TemporaryFolder();
	
	/**
	 * Creates ndex.properties config as a string
	 * @param ndexrootpath value to set for NdexRoot=
	 * @return 
	 */
	public String getConfigAsString(final String ndexrootpath) {
		StringBuilder sb = new StringBuilder();
		sb.append("NdexDBURL=somedburl\n");
		sb.append("NdexSystemUser=sysuser\n");
		sb.append("NdexSystemUserPassword=hithere\n");
		sb.append("NdexRoot=");
		sb.append(ndexrootpath);
		sb.append("\nHostURI=http://localhost\n");
		
		return sb.toString();
	}
	
	/**
	 * Writes configuration file to path specified with root path specified
	 * @param outPath
	 * @param ndexrootpath
	 * @throws IOException 
	 */
	public void writeSimpleConfigToFile(final String outPath,
			final String ndexrootpath) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(outPath));
		bw.write(this.getConfigAsString(ndexrootpath));
		bw.flush();
		bw.close();
	}
	
	/**
	 * Copies aspect files from resource path passed in to destPath directory
	 * @param resourceDirName resource path ie /wntsingaling
	 * @param destPath destination directory
	 * @throws Exception 
	 */
	public void copyNetworkAspects(final String resourceDirName,
			final String destPath) throws Exception {
		List<String> aspects = Arrays.asList("cartesianLayout",
										"cyVisualProperties", 
										"edgeAttributes",
										"edges", 
										"networkAttributes",
										"nodeAttributes",
										"nodes",
										"cyHiddenAttributes",
										"cyTableColumn");
		File aspectDir = new File(destPath + File.separator +  CXNetworkLoader.CX1AspectDir);
		aspectDir.mkdirs();
		for (String aspectName : aspects){
			try {
				String filePath = this.getClass().getResource(resourceDirName + "/" + aspectName).getFile();
				File srcFile = new File(filePath);
				Files.copy(srcFile, new File(aspectDir.getAbsolutePath() + File.separator + srcFile.getName()));
			} catch(NullPointerException npe){
				// ignore cases where a resource is missing, the unit test will
				// probably catch it and some datasets are missing the files 
				// intentionally
			}
		}
	}
	
	/**
	 * Loads resource passed in as a metadatacollection
	 * @param metaDataResource resource to load ie /wntsignaling/metadata
	 * @return MetaDataCollection object
	 * @throws Exception 
	 */
	public MetaDataCollection getNetworkMetaData(final String metaDataResource) throws Exception {
		JsonFactory jf = new JsonFactory();
		
		JsonParser jp = jf.createParser(new File(this.getClass().getResource(metaDataResource).getFile()));
		return MetaDataCollection.createInstanceFromJson(jp);
	}
	
	@Test
	public void testConvertWntSignalingNetwork() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/wntsignaling/metadata");
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/wntsignaling", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		
		// run conversion
		List<CxMetadata> metaDataList = converter.convert();
		
		//verify metaDataList returns valid values
		assertEquals(6, metaDataList.size());
		
		for (CxMetadata mData : metaDataList){
			if (mData.getName().equals("networkAttributes") ||
			    mData.getName().equals("visualProperties") ||
				mData.getName().equals("visualEditorProperties") ||
			    mData.getName().equals("attributeDeclarations")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else if (mData.getName().equals("nodes")){
				assertEquals((long)31, (long)mData.getElementCount());
			} else if (mData.getName().equals("edges")){
				assertEquals((long)72, (long)mData.getElementCount());
			} else {
				fail("Unexpected meta data: " + mData.getName());
			}
		}
		
		//verify we got a network.cx2 file
		File cx2File = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator
		         + CX2NetworkLoader.cx2NetworkFileName);
		assertTrue(cx2File.isFile());
		assertTrue(cx2File.length() > 0);
		
		//verify aspect files for cx2 are properly created
		File a2Dir = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator + CX2NetworkLoader.cx2AspectDirName);
		List<String> cx2AspectFileNames = new ArrayList<>();
		for (String entry : a2Dir.list()){
			cx2AspectFileNames.add(entry);
			File fileCheck = new File(a2Dir.getAbsolutePath() + File.separator + entry);
			assertTrue(entry + " file has 0 size", fileCheck.length() > 0);
		}
		assertEquals(6, cx2AspectFileNames.size());
		assertTrue(cx2AspectFileNames.contains("visualProperties"));
		assertTrue(cx2AspectFileNames.contains("visualEditorProperties"));
		assertTrue(cx2AspectFileNames.contains("nodes"));
		assertTrue(cx2AspectFileNames.contains("edges"));
		assertTrue(cx2AspectFileNames.contains("networkAttributes"));
		assertTrue(cx2AspectFileNames.contains("attributeDeclarations"));
		
		List<String> warnings = converter.getWarning();
		assertEquals(1, warnings.size());
		assertTrue(warnings.get(0).startsWith("CX2-CONVERTER: Failed to parse mapping string 'C'"));
	}
	
	@Test
	public void testConvertWntSignalingNetworkAlwaysCreateFalse() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/wntsignaling/metadata");
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, false);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/wntsignaling", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		
		try {
			converter.convert();
			fail("Expected IOException");
		} catch(IOException e){
			assertTrue(e.getMessage().startsWith("Failed to parse mapping string 'C' in mapping COL=type"));
		}
		
		//TODO Question if conversion fails should files such as network.cx2 remain?
		//     They seem to still exist after a failure
	}
	
	@Test
	public void testConvertCDAPSHierarchyNetwork() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/cdapshier/metadata");
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/cdapshier", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		
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
				assertEquals((long)43, (long)mData.getElementCount());
			} else if (mData.getName().equals("edges")){
				assertEquals((long)37, (long)mData.getElementCount());
			} else if (mData.getName().equals("edgeBypasses")){
				assertEquals((long)2, (long)mData.getElementCount());
			} else if (mData.getName().equals("cyHiddenAttributes")){
				assertEquals((long)3, (long)mData.getElementCount());
			} else if (mData.getName().equals("cyTableColumn")){
				assertEquals((long)27, (long)mData.getElementCount());
			} else {
				fail("Unexpected meta data: " + mData.getName());
			}
		}
		
		//verify we got a network.cx2 file
		File cx2File = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator
		         + CX2NetworkLoader.cx2NetworkFileName);
		assertTrue(cx2File.isFile());
		assertTrue(cx2File.length() > 0);
		
		//verify aspect files for cx2 are properly created
		File a2Dir = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator + CX2NetworkLoader.cx2AspectDirName);
		List<String> cx2AspectFileNames = new ArrayList<>();
		for (String entry : a2Dir.list()){
			cx2AspectFileNames.add(entry);
			File fileCheck = new File(a2Dir.getAbsolutePath() + File.separator + entry);
			assertTrue(entry + " file has 0 size", fileCheck.length() > 0);
		}
		assertEquals(8, cx2AspectFileNames.size());
		assertTrue(cx2AspectFileNames.contains("visualProperties"));
		assertTrue(cx2AspectFileNames.contains("visualEditorProperties"));
		assertTrue(cx2AspectFileNames.contains("nodes"));
		assertTrue(cx2AspectFileNames.contains("edges"));
		assertTrue(cx2AspectFileNames.contains("networkAttributes"));
		assertTrue(cx2AspectFileNames.contains("attributeDeclarations"));
		assertTrue(cx2AspectFileNames.contains("cyHiddenAttributes"));
		assertTrue(cx2AspectFileNames.contains("cyTableColumn"));

		List<String> warnings = converter.getWarning();
		assertEquals(1, warnings.size());
		assertEquals("CX2-CONVERTER: Duplicated network attribute 'version' found.", warnings.get(0));
		
		
	}
	
	@Test
	public void testConvertCDAPSHierarchyNetworkAlwaysCreateFalse() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/cdapshier/metadata");
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, false);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/cdapshier", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		
		// run conversion
		try {
			converter.convert();
			fail("Expected NdexException");
		} catch(NdexException ne){
			assertEquals("Duplicated network attribute 'version' found.", ne.getMessage());
		}
	}

	@Test
	public void testConvertBypassTest() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		
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
		File cx2File = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator
		         + CX2NetworkLoader.cx2NetworkFileName);
		assertTrue(cx2File.isFile());
		assertTrue(cx2File.length() > 0);
		
		//verify aspect files for cx2 are properly created
		File a2Dir = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator + CX2NetworkLoader.cx2AspectDirName);
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
	public void testConvertCartesianCoordinateLacksCorrespondingNode() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		mdc.setElementCount("cartesianLyaout", 3L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		String cartesianLayoutFile = tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator 
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
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, false);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		String cartesianLayoutFile = tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator 
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
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, false);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		String cartesianLayoutFile = tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator 
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
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		String cartesianLayoutFile = tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator 
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
		System.out.println("XXXXXXXX: " + warnings);
		assertEquals(2, warnings.size());
		assertEquals("CX2-CONVERTER: Node attribute id: 62 is named 'name' which is not allowed in CX spec.", warnings.get(0));
		assertEquals("CX2-CONVERTER: Duplicate nodes attribute on id: 62. Attribute 'name' has value (Node 1) and (uhoh)", warnings.get(1));

	}
	
	@Test
	public void testConvertNodeAttributeIsNamedRepresents() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		mdc.setElementCount("nodeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		String cartesianLayoutFile = tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator 
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
		System.out.println("XXXXXXXX: " + warnings);
		assertEquals(1, warnings.size());
		assertEquals("CX2-CONVERTER: Node attribute id: 62 is named 'represents' which is not allowed in CX spec.", warnings.get(0));

	}
	
	@Test
	public void testConvertEdgeAttributeIsNamedInteractionAlwaysCreateFalse() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		mdc.setElementCount("edgeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, false);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		String cartesianLayoutFile = tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator 
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
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/bypasstest/metadata");
		mdc.setElementCount("edgeAttributes", 5L);
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/bypasstest", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		String cartesianLayoutFile = tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator 
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
		System.out.println("XXXXXXXX: " + warnings);
		assertEquals(2, warnings.size());
		assertEquals("CX2-CONVERTER: Edge attribute id: 66 is named 'interaction' which is not allowed in CX spec.", warnings.get(0));
		assertEquals("CX2-CONVERTER: Duplicate edges attribute on id: 66. Attribute 'interaction' has value (wrong) and (interacts with)", warnings.get(1));
	}
	
	@Test
	public void testRudiMappings() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = getNetworkMetaData("/rudimappings/metadata");
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/rudimappings", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		
		// run conversion
		List<CxMetadata> metaDataList = converter.convert();
		
		//verify metaDataList returns valid values
		assertEquals(8, metaDataList.size());
	
		for (CxMetadata mData : metaDataList){
			if (mData.getName().equals("networkAttributes") ||
			    mData.getName().equals("visualProperties") ||
				mData.getName().equals("visualEditorProperties") ||
			    mData.getName().equals("attributeDeclarations")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else if (mData.getName().equals("nodes")){
				assertEquals((long)488, (long)mData.getElementCount());
			} else if (mData.getName().equals("edges")){
				assertEquals((long)487, (long)mData.getElementCount());
			} else if (mData.getName().equals("edgeBypasses")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else if (mData.getName().equals("cyHiddenAttributes")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else if (mData.getName().equals("cyTableColumn")){
				assertEquals((long)33, (long)mData.getElementCount());
			} else if (mData.getName().equals("nodeBypasses")){
				assertEquals((long)1, (long)mData.getElementCount());
			} else {
				fail("Unexpected meta data: " + mData.getName());
			}
		}
		
		//verify we got a network.cx2 file
		File cx2File = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator
		         + CX2NetworkLoader.cx2NetworkFileName);
		assertTrue(cx2File.isFile());
		assertTrue(cx2File.length() > 0);
		
		//verify aspect files for cx2 are properly created
		File a2Dir = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator + CX2NetworkLoader.cx2AspectDirName);
		List<String> cx2AspectFileNames = new ArrayList<>();
		for (String entry : a2Dir.list()){
			cx2AspectFileNames.add(entry);
			File fileCheck = new File(a2Dir.getAbsolutePath() + File.separator + entry);
			assertTrue(entry + " file has 0 size", fileCheck.length() > 0);
		}
		assertEquals(8, cx2AspectFileNames.size());
		assertTrue(cx2AspectFileNames.contains("visualProperties"));
		assertTrue(cx2AspectFileNames.contains("visualEditorProperties"));
		assertTrue(cx2AspectFileNames.contains("nodes"));
		assertTrue(cx2AspectFileNames.contains("edges"));
		assertTrue(cx2AspectFileNames.contains("networkAttributes"));
		assertTrue(cx2AspectFileNames.contains("attributeDeclarations"));
		assertTrue(cx2AspectFileNames.contains("cyTableColumn"));
		assertTrue(cx2AspectFileNames.contains("cyHiddenAttributes"));

		// @TODO The converter reports duplicate warnings about duplicate network
		//       attribute cause its checked in analyzeAttributes and again in convert()
		List<String> warnings = converter.getWarning();
		assertEquals(0, warnings.size());
	}
}
