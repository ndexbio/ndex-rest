package org.ndexbio.common.persistence;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.cxio.core.interfaces.AspectElement;
import org.cxio.util.JsonWriter;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Encapsulate the output stream, writer, JsonWriters that we need to write
 * individual aspects into a file.
 * @author chenjing
 *
 */
public class CXAspectWriter implements AutoCloseable{
	
	private OutputStream out;
	private JsonWriter jwriter;
	private long count;
	
	private static final byte[] start = {'['};
	private static final byte[] comma = {','};
	private static final byte[] end = {']'};
	
	public CXAspectWriter(String aspectFileName) throws IOException {
		out = new FileOutputStream(aspectFileName);
		jwriter = JsonWriter.createInstance(out,true);
	    jwriter.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
	    jwriter.configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false);
		count = 0;
	}
	

	@Override
	public void close () throws IOException {
		out.write(end);
		jwriter.close();
		out.close();
	}


	
	public void writeCXElement(AspectElement e) throws IOException {
		if ( count == 0 ) 
			out.write(start);
			//owriter.write("[");
		else 
			out.write(comma);//owriter.write(","); 
		e.write(jwriter);
		count++;
	}
	
	public void flush() throws IOException { out.flush();}
	

}
