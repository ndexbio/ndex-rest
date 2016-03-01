/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest.helpers;

import java.util.Comparator;
import java.util.Map;

@Deprecated
public class TermDependencyComparator implements Comparator<String> {


//	private Map<String, Term> base;

	@Override
	public int compare(String termId1, String termId2) {
		
		
		// Comparing keys, get the values (Terms) and compare
/*		Term term1 = base.get(termId1);
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
		} */
		return 0;
	}

	// Recursive scan for dependency...
/*	private boolean dependsOn(FunctionTerm term1, String termId2) {
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
	}  */

}
