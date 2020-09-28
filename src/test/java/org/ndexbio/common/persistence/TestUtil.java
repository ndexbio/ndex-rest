package org.ndexbio.common.persistence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.ndexbio.cxio.metadata.MetaDataCollection;

/**
 * Contains some utility functions needed by the tests
 * @author churas
 */
public class TestUtil {
	
	/**
	 * Creates ndex.properties config as a string
	 * @param ndexrootpath value to set for NdexRoot=
	 * @return 
	 */
	public static String getConfigAsString(final String ndexrootpath) {
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
	public static void writeSimpleConfigToFile(final String outPath,
			final String ndexrootpath) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outPath))){
			bw.write(TestUtil.getConfigAsString(ndexrootpath));
			bw.flush();
		}
	}
	
	/**
	 * Copies aspect files from resource path passed in to destPath directory
	 * @param resourceDirName resource path ie /wntsingaling
	 * @param destPath destination directory
	 * @throws Exception 
	 */
	public static void copyNetworkAspects(Class theClass, final String resourceDirName,
			List<String> aspects,
			final String destPath) throws Exception {
		
		File aspectDir = new File(destPath + File.separator +  CXNetworkLoader.CX1AspectDir);
		aspectDir.mkdirs();
		for (String aspectName : aspects){
			try {
				String filePath = theClass.getResource(resourceDirName + "/" + aspectName).getFile();
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
	public static MetaDataCollection getNetworkMetaData(Class theClass, final String metaDataResource) throws Exception {
		JsonFactory jf = new JsonFactory();
		JsonParser jp = jf.createParser(new File(theClass.getResource(metaDataResource).getFile()));
		return MetaDataCollection.createInstanceFromJson(jp);
	}
}
