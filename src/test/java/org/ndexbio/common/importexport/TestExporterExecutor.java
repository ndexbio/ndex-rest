package org.ndexbio.common.importexport;


import java.util.ArrayList;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.util.UUID;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.apache.commons.io.IOUtils;

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
	
	public void writeConfigToFile(final String outPath, final String ndexrootpath) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(outPath));
		bw.write(this.getConfigAsString(ndexrootpath));
		bw.flush();
		bw.close();
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
		writeConfigToFile(configFile, tmpFolder.getCanonicalPath());
		
		// needed to update configuration with custom config
		// cause once jvm is running environment variable cannot be updated
		Configuration.reCreateInstance(configFile);
		
		ExporterExecutor ee = new ExporterExecutor(null);
		UUID auuid = UUID.fromString("54a9a35b-1e5f-11e8-b939-0ac135e8bacf");
		String res = ee.createOutputDirectory(auuid);
		assertEquals(res, tmpFolder.getCanonicalFile() + File.separator +
				          "workspace" + File.separator + auuid.toString());
		
	}
	
	@Test
	public void testNonexistantCommand() throws IOException, NdexException {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";		
		writeConfigToFile(configFile, tmpFolder.getCanonicalPath());
		
		// needed to update configuration with custom config
		// cause once jvm is running environment variable cannot be updated
		Configuration.reCreateInstance(configFile);
		ImporterExporterEntry ie = new ImporterExporterEntry();
		ie.setFileExtension(".txt");
		ie.setDirectoryName(tmpFolder.getCanonicalPath());
		ArrayList<String> mylist = new ArrayList<String>();
		
		mylist.add(tmpFolder.getCanonicalPath() + File.separator + "doesnotexist");
		ie.setExporterCmd(mylist);
		ExporterExecutor ee = new ExporterExecutor(ie);
		InputStream in = IOUtils.toInputStream("hello world", "UTF-8");
		UUID taskid = UUID.randomUUID();
		UUID userid = UUID.randomUUID();
		int result = ee.export(in, taskid, userid);
		assertEquals(-200, result);
		assertTrue(ee.getErrorMessage().contains("Caught IOException"));
		
	}
	
	@Test
	public void testExportSuccessfulCommand() throws IOException, NdexException {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";		
		writeConfigToFile(configFile, tmpFolder.getCanonicalPath());
		
		// needed to update configuration with custom config
		// cause once jvm is running environment variable cannot be updated
		Configuration.reCreateInstance(configFile);
		ImporterExporterEntry ie = new ImporterExporterEntry();
		ie.setFileExtension(".txt");
		ie.setDirectoryName(tmpFolder.getCanonicalPath());
		ArrayList<String> mylist = new ArrayList<String>();
		
		mylist.add("/bin/bash");
		mylist.add("-c");
		mylist.add("tee");
		ie.setExporterCmd(mylist);
		ExporterExecutor ee = new ExporterExecutor(ie);
		InputStream in = IOUtils.toInputStream("hello world", "UTF-8");
		UUID taskid = UUID.randomUUID();
		UUID userid = UUID.randomUUID();
		int result = ee.export(in, taskid, userid);
		in.close();
		assertEquals("If /bin/bash or tee command not available this test will fail", 0, result);
		assertNull(ee.getErrorMessage());
		
		//verify we got a standard out file
		String prefix = ee.getPathPrefix(userid);
		String outfile = ee.getStandardOutFilePath(prefix, taskid.toString());
		File outf = new File(outfile);
		assertTrue(outf.isFile());
		assertEquals(11, outf.length());
		BufferedReader br = new BufferedReader(new FileReader(outf));
		assertEquals("hello world", br.readLine());
		br.close();
		
		//verify we got an empty standard error file
		String errfile = ee.getStandardErrorFilePath(prefix, taskid.toString());
		File errf = new File(errfile);
		assertTrue(errf.isFile());
		assertEquals(0, errf.length());
	}
	
	@Test
	public void testExportFailedCommand() throws IOException, NdexException {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";		
		writeConfigToFile(configFile, tmpFolder.getCanonicalPath());
		
		// needed to update configuration with custom config
		// cause once jvm is running environment variable cannot be updated
		Configuration.reCreateInstance(configFile);
		ImporterExporterEntry ie = new ImporterExporterEntry();
		ie.setFileExtension("txt");
		ie.setDirectoryName(tmpFolder.getCanonicalPath());
		ArrayList<String> mylist = new ArrayList<String>();
		
		String script = "#!/bin/bash\ncat - \necho 'someerror' 1>&2\nexit 7\n";
		String scriptfile = tmpFolder.getCanonicalPath() + File.separator + "script.sh";
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(scriptfile));
		bw.write(script);
		bw.flush();
		bw.close();
		File sfile = new File(scriptfile);
		sfile.setExecutable(true);
		
		mylist.add(scriptfile);
		ie.setExporterCmd(mylist);
		ExporterExecutor ee = new ExporterExecutor(ie);
		InputStream in = IOUtils.toInputStream("hello world\n", "UTF-8");
		UUID taskid = UUID.randomUUID();
		UUID userid = UUID.randomUUID();
		int result = ee.export(in, taskid, userid);
		assertNull(ee.getErrorMessage());
		assertEquals(7, result);
		String prefix = ee.getPathPrefix(userid);
		String outfile = ee.getStandardOutFilePath(prefix, taskid.toString());
		File outf = new File(outfile);
		assertTrue(outf.isFile());
		assertEquals(12, outf.length());
		
		BufferedReader br = new BufferedReader(new FileReader(outfile));
		assertEquals("hello world", br.readLine());
		br.close();
		
		String errorfile = ee.getStandardErrorFilePath(prefix, taskid.toString());
		File errf = new File(errorfile);
		assertTrue(errf.isFile());
		assertEquals(10, errf.length());
		
		br = new BufferedReader(new FileReader(errorfile));
		assertEquals("someerror", br.readLine());
		br.close();
		
		
		
		
	}
}
