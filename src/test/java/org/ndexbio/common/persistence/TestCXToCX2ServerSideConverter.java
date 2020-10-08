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
		TestUtil.copyNetworkAspects(this.getClass(), resourceDirName, aspects, destPath);
	}
	
	@Test
	public void testConvertWntSignalingNetwork() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		TestUtil.writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = TestUtil.getNetworkMetaData(this.getClass(), "/wntsignaling/metadata");
		
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
		assertTrue(warnings.get(0).startsWith(
				"CX2-CONVERTER: Corrupted data found in DISCRETE mapping on NODE_LABEL_POSITION. Please upgrade your cyNDEx-2 and Cytoscape to the latest version and reload this network.\n" +
				"Cause: Failed to parse mapping segment 'C' in mapping COL=type,T=string,K=0=smallmolecule,V=0=C,C,c,0.00,0.00,K=1=antibody,V=1=C,C,c,0.00,0.00,K=2=rna,V=2=C,C,c,0.00,0.00,K=3=chemical,V=3=C,C,c,0.00,0.00,K=4=stimulus,V=4=C,C,c,0.00,0.00,K=5=mirna,V=5=C,C,c,0.00,0.00,K=6=mrna,V=6=C,C,c,0.00,0.00,K=7=signal,V=7="));
	}
	
	@Test
	public void testConvertWntSignalingNetworkAlwaysCreateFalse() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		TestUtil.writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = TestUtil.getNetworkMetaData(this.getClass(), "/wntsignaling/metadata");
		
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, false);
		
		// copy over network aspect files used by conversion
		copyNetworkAspects("/wntsignaling", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		
		//try {
			converter.convert();
		//	fail("Expected IOException");
		//} catch(IOException e){
		//	assertTrue(e.getMessage().startsWith("Failed to parse mapping string 'C' in mapping COL=type"));
		//}
		
		List<String> warnings = converter.getWarning();
		
		assertTrue (warnings.get(0).startsWith("CX2-CONVERTER: Corrupted data found in DISCRETE mapping on NODE_LABEL_POSITION. Please upgrade your cyNDEx-2 and Cytoscape to the latest version and reload this network.\n" +
"Cause: Failed to parse mapping segment 'C' in mapping COL=type,T=string,K=0=smallmolecule,V=0=C,C,c,0.00,0.00,K=1=antibody,V=1=C,C,c,0.00,0.00,K=2=rna,V=2=C,C,c,0.00,0.00,K=3=chemical,V=3=C,C,c,0.00,0.00,K=4=stimulus,V=4=C,C,c,0.00,0.00,K=5=mirna,V=5=C,C,c,0.00,0.00,K=6=mrna,V=6=C,C,c,0.00,0.00,K=7=signal,V=7=C,C,c,0.00,0.00"));
		//TODO Question if conversion fails should files such as network.cx2 remain?
		//     They seem to still exist after a failure
	}
	
	@Test
	public void testConvertCDAPSHierarchyNetwork() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		TestUtil.writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = TestUtil.getNetworkMetaData(this.getClass(), "/cdapshier/metadata");
		
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
		TestUtil.writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = TestUtil.getNetworkMetaData(this.getClass(), "/cdapshier/metadata");
		
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
	public void testRudiMappings() throws Exception {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		TestUtil.writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath());
		String networkIdStr = UUID.randomUUID().toString();
		
		// Load the meta data from file in resources
		MetaDataCollection mdc = TestUtil.getNetworkMetaData(this.getClass(),"/rudimappings/metadata");
		
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
