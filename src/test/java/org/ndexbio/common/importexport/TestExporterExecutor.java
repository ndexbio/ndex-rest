package org.ndexbio.common.importexport;

import java.io.IOException;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.UUID;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;


@RunWith(JUnit4.class)
public class TestExporterExecutor {

	@Rule
    public TemporaryFolder _tmpFolder = new TemporaryFolder();
	
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
	
	@Test
	public void testConstructorWithNull() {
		ExporterExecutor ee = new ExporterExecutor(null);
		assertNotNull(ee);
	}
	
	@Test
	public void testCreateOutputDirectoryWithNullUUID() {
		ExporterExecutor ee = new ExporterExecutor(null);
		String res = ee.createOutputDirectory(null);
		assertEquals(res, null);
		assertEquals(ee.getErrorMessage(), "User UUID is null");
	}
	
	@Test
	public void testCreateDirectoryIfNeeded() throws IOException {
		File tmpFolder = _tmpFolder.newFolder();
		ExporterExecutor ee = new ExporterExecutor(null);
		
		// test where directory exists
		boolean res = ee.createDirectoryIfNeeded(tmpFolder.getCanonicalPath());
		assertTrue(res);
		assertNull(ee.getErrorMessage());
		
		// test where directory needs to be created
		
		String subsubdir = tmpFolder.getCanonicalPath() + File.separator + "hi" + 
		                File.separator + "bye";
		res = ee.createDirectoryIfNeeded(subsubdir);
		assertTrue(res);
		assertNull(ee.getErrorMessage());
		File checkDir = new File(subsubdir);
		assertTrue(checkDir.isDirectory());
		
		// test where file already exists with name so mkdir will fail
		File aF = new File(tmpFolder.getCanonicalPath() + "existingfile");
		assertTrue(aF.createNewFile());
		
		res = ee.createDirectoryIfNeeded(aF.getCanonicalPath());
		assertFalse(res);
		assertEquals(ee.getErrorMessage(), aF.getCanonicalPath() +
				     " exists, but is not a directory");
	}
	
	@Test
	public void testCreateOutputDirectorySuccess() throws IOException, NdexException {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		BufferedWriter bw = new BufferedWriter(new FileWriter(configFile));
		bw.write(this.getConfigAsString(tmpFolder.getCanonicalPath()));
		bw.flush();
		bw.close();
		
		// needed to update configuration with custom config
		// cause once jvm is running environment variable cannot be updated
		Configuration.reCreateInstance(configFile);
		
		ExporterExecutor ee = new ExporterExecutor(null);
		UUID auuid = UUID.fromString("54a9a35b-1e5f-11e8-b939-0ac135e8bacf");
		String res = ee.createOutputDirectory(auuid);
		assertEquals(res, tmpFolder.getCanonicalFile() + File.separator +
				          "workspace" + File.separator + auuid.toString());
		
	}

}
