package org.ndexbio.rest.models;

import java.util.Map;

import org.ndexbio.rest.exceptions.NdexException;

/*
 * mod 25Nov2013 
 * eliminate JDexId field
 * the Term class allows us to group specific term objects in the same 
 * collection type
 */

public class Term extends NdexObject
{
	private String name;
    private String namespace;
    private String termFunction;
    private Map<Integer, String> parameters;
    
    private String termType;
    
    
    public Term(){
    	this.termType = "BASE";
    }

	public String getName() {
		return this.name;
	}

	public void setName(String termName) {
		this.name = termName;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getTermFunction() {
		
		return termFunction;
	}

	public void setTermFunction(String termFunction) {
		this.setTermType("FUNCTION");
		this.termFunction = termFunction;
	}

	public Map<Integer, String> getParameters() {
		return parameters;
	}
	
	public void putParameter(Integer index, String parameter) throws NdexException{
		if(this.getParameters().get(index) != null){
			throw new NdexException("Term parameter JDexId collision");
		}
		this.getParameters().put(index, parameter);
	}

	public void setParameters(Map<Integer, String> parameters) {
		this.setTermType("FUNCTION");
		this.parameters = parameters;
	}

	public String getTermType() {
		return termType;
	}

	private void setTermType(String termType) {
		this.termType = termType;
	}
}
