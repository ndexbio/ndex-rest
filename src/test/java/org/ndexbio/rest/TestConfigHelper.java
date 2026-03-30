package org.ndexbio.rest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

/**
 * Test utility for initializing the Configuration singleton from test classes
 * outside the org.ndexbio.rest package. Configuration.reCreateInstance() is
 * protected, so access must go through this same-package helper.
 */
public class TestConfigHelper {

    public static void initIfNeeded() throws Exception {
        if (Configuration.getInstance() != null) return;
        File tmpDir = Files.createTempDirectory("ndex-test").toFile();
        File configFile = new File(tmpDir, "ndex.properties");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile))) {
            bw.write("NdexDBURL=somedburl\nNdexSystemUser=sysuser\nNdexSystemUserPassword=hithere\n");
            bw.write("NdexRoot=" + tmpDir.getAbsolutePath() + "\nHostURI=http://localhost\n");
            bw.write("NDEX_KEY=effggekk\nDOI_CREATOR=CqmUkFe5kW5sJVMKNQYYLg==\n");
        }
        Configuration.reCreateInstance(configFile.getAbsolutePath());
    }
}
