package org.ndexbio.rest.helpers;

import java.util.Comparator;
import java.util.Map;

import org.ndexbio.common.models.object.FunctionTerm;
import org.ndexbio.common.models.object.Term;

public class TermDependencyComparator implements Comparator<String> {


	private Map<String, Term> base;

	@Override
	public int compare(String termId1, String termId2) {
		// Comparing keys, get the values (Terms) and compare
		Term term1 = base.get(termId1);
		Term term2 = base.get(termId2);
		
		// if both are function terms, if term1 depends on term2, then term2 comes first		
		// if one is a functionTerm and one is a baseTerm, baseTerm comes first		
		if (term1.getTermType().equals("Function")){
			
			if (term2.getTermType().equals("Function")){
				
				if (dependsOn((FunctionTerm)term1, termId2))
					return 1; // term1 depends on term2, term1 comes second

				if (dependsOn((FunctionTerm)term2, termId1))
					return -1;	// term2 depends on term1, term1 comes first
				
				return 0; // term1 and term2 are independent function terms, preserve order
				
			} else {
				return 1;  // term2 is BaseTerm, functionTerm term1 comes second
			}
		} else if (term2.getTermType().equals("Function")){
			return -1; // term1 is BaseTerm while term2 is functionTerm, term1 comes first
		} else {
			return 0;  // default: terms have same priority, preserve order
		}
	}

	// Recursive scan for dependency...
	private boolean dependsOn(FunctionTerm term1, String termId2) {
		for (String parameterId : term1.getParameters().values()){
			if (parameterId == termId2) return true;
			Term parameterTerm = base.get(parameterId);
			if (parameterTerm.getTermType().equals("Function"))
				if (dependsOn((FunctionTerm) parameterTerm, termId2)) return true;
		}
		return false;
	}

	public TermDependencyComparator (Map<String, Term> base){
		this.base = base;
	}

}
