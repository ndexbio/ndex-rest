package org.ndexbio.common.persistence;

import java.util.Set;
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
	private Set<Long> undefinedIds;
	
	private String aspectName;
	
	public AspectElementIdTracker(String aspectName) {
		definedIds = new TreeSet<>();
		undefinedIds = new TreeSet<>();
		this.aspectName = aspectName;
		
	}
	
	public void addReferenceId(Long id) {
		if (!definedIds.contains(id))
			undefinedIds.add(id);
	}
	
	public void addDefinedElementId(long id) throws NdexException {
		
		Long ID = Long.valueOf(id);
		if ( !this.definedIds.add(ID)) {
			throw new NdexException ("Duplicate Id " + id + " found in aspect" + aspectName );
		}
		
		undefinedIds.remove(ID);
	}
	
	public boolean hasUndefinedIds () {return undefinedIds.size()>0;}
	
	public Set<Long> getUndefinedIds() { return undefinedIds;}
	
	public int getDefinedElementSize() { return definedIds.size(); }
	
	
	public String checkUndefinedIds ()  {
		if ( undefinedIds.size() > 0 ) {
		  String errorMessage = undefinedIds.size() + " undefined ids found in aspect " + aspectName + ": [";
		  for( Long sid : undefinedIds)
			  errorMessage += sid + " ";	
		  errorMessage +="]";
		  return errorMessage;
		} 
		return null;
	}
	
}
