package org.ndexbio.task;

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


/**
 *
 * @author churas
 */
@RunWith(JUnit4.class)
public class TestNetworkExportTask {
    
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
    public void testcall_aux_with_null_task() {
    	NetworkExportTask exptask = new NetworkExportTask(null);
    	try {
    		exptask.call_aux();
    		fail("Expected exception");
    	} catch(NullPointerException npe) {
			// newer versions of java like 21 output more information
			// when a nullpointerexception is created instead of null
			assertTrue(npe.getMessage() == null || npe.getMessage().contains("is null"));
    	} catch(Exception ex) {
    		fail("Expected NullPointerException");
    	}
    }
        

}
