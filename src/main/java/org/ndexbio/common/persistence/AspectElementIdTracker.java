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
	
	/**
	 * Adds element id if it is not already added in defined element list 
	 * via {@link #addDefinedElementId}
	 * @param id the id of element
	 * @param aspect name of aspect
	 */
	public void addReferenceId(Long id, String aspect) {
		if (!definedIds.contains(id))
			undefinedIds.put(id, aspect);
	}
	
	/**
	 * Adds defined element id, removing undefined element with same {@code id}
	 * @param id the id of element
	 * @throws NdexException if {@code id} has already been added
	 */
	public void addDefinedElementId(Long id) throws NdexException {
		
		if ( !this.definedIds.add(id)) {
			throw new NdexException ("Duplicate Id " + id + " found in aspect " + aspectName );
		}
		
		undefinedIds.remove(id);
	}
	
	/**
	 * Tells caller if there are any undefined elements
	 * @return {@code TRUE} if there are otherwise false
	 */
	public boolean hasUndefinedIds () {return undefinedIds.size()>0;}
	
	/**
	 * Gets the undefined ids
	 * @return 
	 */
	public Map<Long,String> getUndefinedIds() { return undefinedIds;}
	
	/**
	 * Gets size of defined elements
	 * @return 
	 */
	public int getDefinedElementSize() { return definedIds.size(); }
	
	/**
	 * Examines the undefined elements in this object and creates
	 * a string report of the first 20 elements that are not defined.
	 * If more then 20 elements are found the string is ended with {@code ,...} instead
	 * of single {@code .} but the count at the start of the report is still correct
	 * 
	 * @return {@code null} or String reporting details about missing elements in aspect
	 */
	public String checkUndefinedIds ()  {
		if ( undefinedIds.size() > 0 ) {
			StringBuilder sb = new StringBuilder();
			sb.append("There are ");
			sb.append(undefinedIds.size());
			sb.append(" missing elements in aspect ");
			sb.append(aspectName);
			sb.append(", and these are the element ids that are referenced in other aspects but missing in this aspect: ");
			int i = 0;
			for( Map.Entry<Long,String> entry : undefinedIds.entrySet()) {
				if (i == 20) {
					sb.append(",..");
					break;
				}
				if (i >0) 
					sb.append(", ");
				sb.append(entry.getKey());
				sb.append(" in ");
				sb.append(entry.getValue());
				i++;
			}
			sb.append(".");
			return sb.toString();
		} 
		return null;
	}
}
