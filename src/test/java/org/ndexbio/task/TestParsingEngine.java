package org.ndexbio.task;

import java.net.URL;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;
import org.ndexbio.xbel.parser.ExcelFileParser;
import org.ndexbio.xbel.parser.SIFFileParser;
import org.ndexbio.xbel.parser.XbelFileParser;
import org.ndexbio.xbel.service.JdexIdService;

public class TestParsingEngine
{
    @Test
    public void createJdexIds()
    {
        final ArrayList<Long> jdexIds = new ArrayList<Long>();
        for (int idIndex = 0; idIndex < 20; idIndex++)
            jdexIds.add(JdexIdService.INSTANCE.getNextJdexId());
        
        Assert.assertEquals(jdexIds.size(), 20);
    }

    @Test
    public void parseExcelFile() throws Exception
    {
        final URL smallExcelNetworkUrl = getClass().getResource("/resources/small-excel-network.xls");
        final ExcelFileParser excelParser = new ExcelFileParser(smallExcelNetworkUrl.toURI().getPath());
        excelParser.parseExcelFile();
    }
    
    @Test
    public void parseLargeExcelFile() throws Exception
    {
        final URL smallExcelNetworkUrl = getClass().getResource("/resources/large-excel-network.xls");
        final ExcelFileParser excelParser = new ExcelFileParser(smallExcelNetworkUrl.toURI().getPath());
        excelParser.parseExcelFile();
    }
    
    @Test
    public void parseSifFile() throws Exception
    {
        final URL galNetworkUrl = getClass().getResource("/resources/gal-filtered.sif");
        final SIFFileParser sifParser = new SIFFileParser(galNetworkUrl.toURI().getPath());
        sifParser.parseSIFFile();
    }
    
    @Test
    public void parseXbelFile() throws Exception
    {
        final URL galNetworkUrl = getClass().getResource("/resources/tiny-corpus.xbel");
        final XbelFileParser xbelParser = new XbelFileParser(galNetworkUrl.toURI().getPath());
        
        if (xbelParser.getValidationState().isValid())
            xbelParser.parseXbelFile();
    }
    
    @Test
    public void parseLargeXbelFile() throws Exception
    {
        final URL galNetworkUrl = getClass().getResource("/resources/small-corpus.xbel");
        final XbelFileParser xbelParser = new XbelFileParser(galNetworkUrl.toURI().getPath());
        
        if (xbelParser.getValidationState().isValid())
            xbelParser.parseXbelFile();
    }
}
