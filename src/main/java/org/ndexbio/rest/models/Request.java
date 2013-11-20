package org.ndexbio.rest.models;

import java.util.Date;

import org.ndexbio.rest.domain.IRequest;


/*
 * POJO class to represent the fields associated with a request
 * extends NdexModel
 */
public class Request extends NdexModel{
	
	private String fromId;
    private String toId;
    private String aboutId;
    private String message;
    private String requestType;
    
    
    public Request() {
    	super();
    }
    
    public Request(IRequest xr) {
    	this();
    	//TODO: resolve missing setters
    	//this.setId(id);
    	//this.setFromId(fromId);
    	//this.setToId(toId);
    	//this.setAboutId(aboutId);
    	this.setMessage(xr.getMessage());
    	this.setRequestType(xr.getRequestType());
    	this.setRequestDate(xr.getRequestTime());   	
    }
  
	public String getFromId() {
		return fromId;
	}
	public void setFromId(String fromId) {
		this.fromId = fromId;
	}
	public String getToId() {
		return toId;
	}
	public void setToId(String toId) {
		this.toId = toId;
	}
	public String getAboutId() {
		return aboutId;
	}
	public void setAboutId(String aboutId) {
		this.aboutId = aboutId;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public Date getRequestDate() {
		return this.getDate();
	}

	public void setRequestDate(Date requestDate) {
		this.setDate(requestDate);
	}

	public String getRequestType() {
		return requestType;
	}
	public void setRequestType(String requestType) {
		this.requestType = requestType;
	}
	

}
