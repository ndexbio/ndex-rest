package org.ndexbio.rest;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestConfigurationKeycloakClientId {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private void writeConfig(File file, String extra) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write("NdexDBURL=somedburl\n");
            bw.write("NdexSystemUser=sysuser\n");
            bw.write("NdexSystemUserPassword=hithere\n");
            bw.write("NdexRoot=" + file.getParent() + "\n");
            bw.write("HostURI=https://ndexbio.org\n");
            bw.write("NDEX_KEY=effggekk\n");
            bw.write("DOI_CREATOR=CqmUkFe5kW5sJVMKNQYYLg==\n");
            bw.write("MigrationPassword=migration\n");
            if (extra != null) bw.write(extra);
        }
    }

    @Test
    public void returnsClientIdWhenPresent() throws Exception {
        File cfg = tmpFolder.newFile("ndex.properties");
        writeConfig(cfg, "KEYCLOAK_CLIENT_ID=myclient\n");
        Configuration.reCreateInstance(cfg.getAbsolutePath());
        assertEquals("myclient", Configuration.getInstance().getKeycloakClientId());
    }

    @Test
    public void returnsNullWhenAbsent() throws Exception {
        File cfg = tmpFolder.newFile("ndex.properties");
        writeConfig(cfg, null);
        Configuration.reCreateInstance(cfg.getAbsolutePath());
        assertNull(Configuration.getInstance().getKeycloakClientId());
    }
}
