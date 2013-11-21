package org.ndexbio.rest.models;

import java.util.Date;

import org.ndexbio.rest.helpers.RidConverter;

import com.google.common.base.Strings;
import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.frames.VertexFrame;

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
	
	public NdexModel(VertexFrame vf){
		this();
		this.setId(resolveVertexId(vf));
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
	
	protected String resolveVertexId(VertexFrame vf){
	   if( null == vf){
		   return null;
	   }
	   return RidConverter.convertToJid((ORID)vf.asVertex().getId());
	}

}
