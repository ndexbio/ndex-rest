package org.ndexbio.common.persistence.orientdb;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.cxio.core.interfaces.AspectElement;
import org.cxio.util.JsonWriter;

/**
 * Encapsulate the output stream, writer, JsonWriters that we need to write
 * individual aspects into a file.
 * @author chenjing
 *
 */
public class CXAspectWriter implements AutoCloseable{
	
	private OutputStream out;
	private JsonWriter jwriter;
	private OutputStreamWriter owriter;
	private long count;
	
	public CXAspectWriter(String aspectFileName) throws IOException {
		out = new FileOutputStream(aspectFileName);
		owriter = new OutputStreamWriter (out);
		jwriter = JsonWriter.createInstance(out,true);
		count = 0;
	}
	

	@Override
	public void close () throws IOException {
		owriter.close();
		out.close();
	}

	
	public void writeStrig(String s) throws IOException {
		owriter.write(s);
	}
	
	public void writeCXElement(AspectElement e) throws IOException {
		if ( count == 0 ) 
			owriter.write("[");
		else 
			owriter.write(",");
		e.write(jwriter);
		count++;
	}

}
