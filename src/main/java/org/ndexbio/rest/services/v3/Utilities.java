package org.ndexbio.rest.services.v3;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class Utilities {
	
	public static CxAttributeDeclaration getAttributeDecls(UUID networkId) throws JsonParseException, JsonMappingException, IOException {
		String aspectDir = Configuration.getInstance().getNdexRoot() + File.separator + "data" +File.separator + 
				networkId + File.separator + CX2NetworkLoader.cx2AspectDirName+  File.separator;
		File vsFile = new File(aspectDir + CxAttributeDeclaration.ASPECT_NAME);
	
		if ( !vsFile.exists())
			return null;
		ObjectMapper om = new ObjectMapper();
		
		CxAttributeDeclaration[] ds = om.readValue(vsFile, CxAttributeDeclaration[].class); 
		
		if (ds.length==0)
			return null;
		return ds[0];
		
	}

}
