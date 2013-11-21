package org.ndexbio.rest.models;

import java.util.Date;

import org.ndexbio.rest.domain.ITask;


/*
 * POJO class to represent the fields associated with a task
 * extends NdexModel
 */

public class Task extends NdexModel {
	
	private String ownerId;
	private String status;

	public Task(ITask it){
		super(it);
		this.setOwnerId(resolveVertexId(it.getOwner()));
		this.setStatus(it.getStatus());
		this.setStartTime(it.getStartTime());
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	public Date getStartTime(){
		return this.getDate();
	}
	
	public void setStartTime(Date start) {
		this.setDate(start);
	}
	
	
}
