package org.ndexbio.common.cx;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class AspectIterator<E> implements Iterator<E>, Closeable {

	private FileInputStream inputStream;
//	private String aspectName;
	private Class typeReference;
//	private UUID networkId;
	private Iterator<E> it;
	
	
	public AspectIterator (UUID networkUUID, String aspectName, Class cls) throws JsonProcessingException, IOException {
		/*networkId = networkUUID;
		this.aspectName = aspectName; */
		typeReference = cls; 
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID + "/aspects/"; 
		
		java.nio.file.Path aspectFile = Paths.get(pathPrefix + aspectName);
		if ( Files.exists(aspectFile)) { 
			 inputStream = new FileInputStream(pathPrefix + aspectName) ;
		  //   Type sooper = getClass().getGenericSuperclass();
		     
		     it = new ObjectMapper().readerFor(TypeFactory.defaultInstance().constructType(cls)).readValues(inputStream);

		} else 
			inputStream = null;
		
		
	}
	
	public AspectIterator (FileInputStream in, Class cls) throws JsonProcessingException, IOException {
		typeReference = cls; 
		inputStream = in;	     
	    it = new ObjectMapper().readerFor(TypeFactory.defaultInstance().constructType(cls)).readValues(inputStream);
	}
	
	
	
	@Override
	public void close() throws IOException {
		if (inputStream != null ) {
			inputStream.close();
			inputStream = null;
		}
	}

	@Override
	public boolean hasNext() {
		if (inputStream !=null)
			return it.hasNext();
		return false;
	}

	@Override
	public E next() {
		if ( inputStream !=null)
			return it.next();
		
		throw new NoSuchElementException ("No more element to return.");
	}

}
