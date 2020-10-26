package org.ndexbio.common.importexport;

import java.util.ArrayList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author churas
 */

@RunWith(JUnit4.class)
public class TestImporterExporterEntry {
    
    @Test
    public void testGetterAndSetters(){
        ImporterExporterEntry entry = new ImporterExporterEntry();
        assertEquals(null, entry.getDescription());
        assertEquals(null, entry.getDirectoryName());
        assertEquals(null, entry.getExporterCmd());
        assertEquals(null, entry.getFileExtension());
        assertEquals(null, entry.getImporterCmd());
        assertEquals(null, entry.getName());
        entry.setDescription("description");
        entry.setDirectoryName("directory");
        List<String> exportercmd = new ArrayList<>();
        exportercmd.add("exporter");
        entry.setExporterCmd(exportercmd);
        entry.setFileExtension("extension");
        List<String> importercmd = new ArrayList<>();
        importercmd.add("importer");
        entry.setImporterCmd(importercmd);
        entry.setName("name");
        assertEquals("description", entry.getDescription());
        assertEquals("directory", entry.getDirectoryName());
        assertEquals("exporter", entry.getExporterCmd().get(0));
        assertEquals("extension", entry.getFileExtension());
        assertEquals("importer", entry.getImporterCmd().get(0));
        assertEquals("name", entry.getName());
        
    }
}
