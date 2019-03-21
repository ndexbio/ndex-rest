package org.ndexbio.common.persistence;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.ndexbio.model.exceptions.NdexException;

/**
 * Helper class for CXNetworkLoading. It tracks which ids are referenced and which id has been defined in CX.
 * @author chenjing
 *
 */
public class AspectElementIdTracker {
	
	//This set stores the ids that have concrete object in CX.
	private Set<Long> definedIds;
	
	//This set tracks the Ids that are referenced in a CX document but we haven't find a 
	// the definition of that element yet.
	private Map<Long,String> undefinedIds;
	
	private String aspectName;
	
	public AspectElementIdTracker(String aspectName) {
		definedIds = new TreeSet<>();
		undefinedIds = new TreeMap<>();
		this.aspectName = aspectName;
		
	}
	
	public void addReferenceId(Long id, String aspect) {
		if (!definedIds.contains(id))
			undefinedIds.put(id, aspect);
	}
	
	public void addDefinedElementId(long id) throws NdexException {
		
		Long ID = Long.valueOf(id);
		if ( !this.definedIds.add(ID)) {
			throw new NdexException ("Duplicate Id " + id + " found in aspect" + aspectName );
		}
		
		undefinedIds.remove(ID);
	}
	
	public boolean hasUndefinedIds () {return undefinedIds.size()>0;}
	
	public Map<Long,String> getUndefinedIds() { return undefinedIds;}
	
	public int getDefinedElementSize() { return definedIds.size(); }
	
	
	public String checkUndefinedIds ()  {
		if ( undefinedIds.size() > 0 ) {
		  String errorMessage = "There are " + undefinedIds.size() + " missing elements in aspect " + aspectName + 
				  ", and these are the element ids that are referenced in other aspects but missing in this aspect: ";
		  int i = 0;
		  for( Map.Entry<Long,String> entry : undefinedIds.entrySet()) {
			  if (i == 20) break;
			  if (i >0) 
				  errorMessage += ", ";
			  errorMessage += entry.getKey() +  " in " + entry.getValue();
			  i++;
		  }	  
		  errorMessage +=".";
		  return errorMessage;
		} 
		return null;
	}
	
}
