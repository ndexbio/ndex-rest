package org.ndexbio.rest;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.ndexbio.rest.Configuration;
import org.ndexbio.model.exceptions.NdexException;

@RunWith(JUnit4.class)
public class TestConfiguration {

	@Rule
    public TemporaryFolder _tmpFolder = new TemporaryFolder();
	
	
	public String getConfigAsString(final String ndexrootpath,
			final String exportertimeout) {
		StringBuilder sb = new StringBuilder();
		sb.append("NdexDBURL=somedburl\n");
		sb.append("NdexSystemUser=sysuser\n");
		sb.append("NdexSystemUserPassword=hithere\n");
		sb.append("NdexRoot=");
		sb.append(ndexrootpath);
		sb.append("\nHostURI=http://localhost\n");
		if (exportertimeout != null) {
			sb.append(Configuration.NDEX_EXPORTER_TIMEOUT);
			sb.append("=");
			sb.append(exportertimeout);
			sb.append("\n");
		}
		return sb.toString();
	}
	
	public void writeSimpleConfigToFile(final String outPath, final String ndexrootpath,
			final String exportertimeout) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(outPath));
		bw.write(this.getConfigAsString(ndexrootpath, exportertimeout));
		bw.flush();
		bw.close();
	}
	
	@Test
	public void getExporterTimeoutDefault() throws IOException {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath(), null);
		try {
			Configuration.reCreateInstance(configFile);
			assertEquals(600, Configuration.getInstance().getExporterTimeout());
		} catch(NdexException ne) {
			fail("Caught NdexException: " + ne.getMessage());
		}
	}
	
	@Test
	public void getExporterSetTimeout() throws IOException {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath(), "250");
		try {
			Configuration.reCreateInstance(configFile);
			assertEquals(250, Configuration.getInstance().getExporterTimeout());
		} catch(NdexException ne) {
			fail("Caught NdexException: " + ne.getMessage());
		}
	}
	
	@Test
	public void getExporterNonNumericTimeout() throws IOException {
		File tmpFolder = _tmpFolder.newFolder();
		String configFile = tmpFolder.getCanonicalPath() + File.separator + "config";
		writeSimpleConfigToFile(configFile, tmpFolder.getCanonicalPath(), "hello");
		try {
			Configuration.reCreateInstance(configFile);
			assertEquals(600, Configuration.getInstance().getExporterTimeout());
		} catch(NdexException ne) {
			fail("Caught NdexException: " + ne.getMessage());
		}
	}
	
	

}
