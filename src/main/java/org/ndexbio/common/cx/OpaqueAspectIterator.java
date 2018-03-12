package org.ndexbio.common.cx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.cxio.misc.OpaqueElement;
import org.cxio.misc.OpaqueFragmentReader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class OpaqueAspectIterator implements Iterator<OpaqueElement> {

	//private FileInputStream inputStream;
//	private String aspectName;
//	private Class typeReference;
//	private UUID networkId;
	
    private JsonParser                                  jp;
    private ObjectMapper                                _m;
    private OpaqueFragmentReader                        _reader;
    private OpaqueElement _current;
	
	public OpaqueAspectIterator (InputStream in) throws JsonParseException, IOException {
	//	inputStream = in;	
        final JsonFactory f = new JsonFactory();
        jp = f.createParser(in);
        _m = new ObjectMapper();

   	 	if (jp.nextToken() != JsonToken.START_ARRAY) {
   	 		throw new IllegalStateException("Wrong file format format: expected to start with an array, but has: " + jp.getCurrentToken().asString());
   	 	} 
   	 	_current = null;
   	 	_reader = OpaqueFragmentReader.createInstance( "a");
	}
	

	@Override
	public boolean hasNext() {
			try {
				while (jp.nextToken() != null) {
					JsonToken token = jp.getCurrentToken();
					if ( token == JsonToken.END_ARRAY) {  //End of Aspect fragment list.
							break;
					}	
					final ObjectNode o = _m.readTree(jp);
				    _current = _reader.readElement(o);
				    if ( _current == null)
				      	throw new RuntimeException ("Malformed object ecountered at line: " + jp.getCurrentLocation().getLineNr() + ", column: "
								+ jp.getCurrentLocation().getColumnNr() );
				    return true;
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("Failed o read aspect file: " + e.getMessage(), e);
			}
		
        _current = null;
        return false;

	}

	@Override
	public OpaqueElement next() {
		if ( _current != null)
			return _current;
		throw new NoSuchElementException ("No more element to return.");
	}

}
