package org.ndexbio.common.persistence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.google.common.io.Files;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;

/**
 *
 * @author churas
 */
public class TestCXToCX2ServerSideConverter {
	
	@Rule
    public TemporaryFolder _tmpFolder = new TemporaryFolder();
	
	public static final String WNT_SIGNALING_DIR = "wntsignaling";
	
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
	
	public void writeSimpleConfigToFile(final String outPath,
			final String ndexrootpath) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(outPath));
		bw.write(this.getConfigAsString(ndexrootpath));
		bw.flush();
		bw.close();
	}
	
	public void copyNetworkAspects(final String resourceDirName,
			final String destPath) throws Exception {
		List<String> aspects = Arrays.asList("cartesianLayout",
										"cyVisualProperties", 
										"edgeAttributes",
										"edges", 
										"networkAttributes",
										"nodeAttributes",
										"nodes");
		File aspectDir = new File(destPath + File.separator +  CXNetworkLoader.CX1AspectDir);
		aspectDir.mkdirs();
		System.out.println("Created: " + aspectDir.getAbsolutePath());
		for (String aspectName : aspects){
			String filePath = this.getClass().getResource(resourceDirName + "/" + aspectName).getFile();
			System.out.println("Copying: " + filePath);
			File srcFile = new File(filePath);
			Files.copy(srcFile, new File(aspectDir.getAbsolutePath() + File.separator + srcFile.getName()));
		}
	}
	
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
		MetaDataCollection mdc = getNetworkMetaData("/wntsignaling/metadata");
		for (MetaDataElement mde : mdc.getMetaData()){
			System.out.println("orig metadata: " + mde.getName() + " => " + mde.getElementCount());
		}
		CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(tmpFolder.getAbsolutePath() + File.separator, 
				mdc, networkIdStr, null, true);
		
		copyNetworkAspects("/wntsignaling", tmpFolder.getAbsolutePath() + File.separator + networkIdStr);
		File aspectDir = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator + CXNetworkLoader.CX1AspectDir);
		
		List<CxMetadata> metaDataList = converter.convert();
		for (CxMetadata mData : metaDataList){
			System.out.println(mData.getName() + " => " + mData.getElementCount());
		}
		
		File baseDir = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr);
		for (String entry : baseDir.list()){
			System.out.println("HHHH: " + entry);
		}
		
		File a2Dir = new File(tmpFolder.getAbsolutePath()
				+ File.separator + networkIdStr + File.separator + CX2NetworkLoader.cx2AspectDirName);
		for (String entry : a2Dir.list()){
			System.out.println("IIII: " + entry);
			BufferedReader br = new BufferedReader(new FileReader(a2Dir.getAbsolutePath() + File.separator + entry));
			while (br.ready()){
				System.out.println(br.readLine());
			}
		}
		
		
	}
}
