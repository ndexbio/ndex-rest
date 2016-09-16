package org.ndexbio.common.cx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.cxio.metadata.MetaDataCollection;
import org.cxio.misc.NumberVerification;
import org.cxio.misc.Status;
import org.cxio.util.CxConstants;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NdexCXNetworkWriter {
	
//	private UUID networkId;
	private OutputStream out;
	private OutputStreamWriter writer;
	private ObjectMapper objectMapper;
	private JsonGenerator g;
		
	public NdexCXNetworkWriter ( OutputStream outputStream) throws IOException {
	//	networkId = networkUUID;
		out = outputStream;
		writer = new OutputStreamWriter(out);
		objectMapper = new ObjectMapper();
		g  = (new JsonFactory()).createGenerator(writer);
		g.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
		g.configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false);
	}
	

	private void writeObject(Object obj) throws JsonProcessingException, IOException {
		writer.write(objectMapper.writeValueAsString( obj));
		writer.flush();
	}
	
	
	public void start() throws IOException {
		writer.write("[");
		
		NumberVerification nv = new NumberVerification(CxConstants.LONG_NUMBER_TEST);
		writeObject(nv);
		writer.write(",");
		writer.flush();
	}
	
	public void writeMetadata(MetaDataCollection m) throws JsonProcessingException, IOException{
		writeObject(m);
		writer.write(",");
	}
	
	public void writeAspectFragment(CXAspectFragment fragment) throws IOException  {		
		g.writeStartObject();
		g.writeObjectField(fragment.getAspectName(), fragment.getElements());
		g.writeEndObject();
		g.flush();
		writer.write(",");
		writer.flush();
	}
	
	public void startAspectFragment(String aspectName) throws IOException {
		g.writeStartObject();
		g.writeFieldName(aspectName);
		g.flush();
	}
	
	public void writeAspectElementsFromNdexAspectFile(String filePath) throws IOException {
		Path p = Paths.get(filePath);
		Files.copy(p, out);		
	}
	
	
	public void endAspectFragment() throws IOException {
		g.writeEndObject();
		g.flush();
		writer.write(",");
		writer.flush();
	}
	
	public void end() throws JsonProcessingException, IOException {
		Status s = new Status (true);
		writeObject(s);
		writer.write("]");
		writer.flush();
		g.close();
	}
}
