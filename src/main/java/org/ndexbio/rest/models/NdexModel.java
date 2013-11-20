package org.ndexbio.rest.models;

import java.util.Date;

/*
 * Abstract class to represent fields and methods common to all
 * Ndex model classes
 */

public abstract class NdexModel {
	
	private String id;
	private Date date;
	
	public NdexModel() {
		this.date = new Date();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	/*
	 * subclasses have more specific public methods for their date field
	 */

	protected Date getDate() {
		return date;
	}

	protected void setDate(Date date) {
		this.date = date;
	}

}
