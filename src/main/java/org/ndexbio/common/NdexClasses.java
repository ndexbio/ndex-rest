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
package org.ndexbio.common;

/**
 *  This Class contains the vertex types (OrientDB classes) used in Ndex database.
 *   
 * @author chenjing
 *
 */
public interface NdexClasses {
	
	public static final String Group          	  = "ndex_group";
	public static final String Membership         = "ndex_group_user";
	public static final String Task				  = "task";
	public static final String User				  = "ndex_user";
	public static final String Network            = "network";
	

	
	//presentationProperty
	public static final String SimpleProp_P_name  = "name";
	public static final String SimpleProp_P_value = "value";
	
	//account edges

    public static final String Account_imageURL  = "image_url";
    public static final String Account_websiteURL = "website_url";
    public static final String Account_description = "description";
    public static final String Account_otherAttributes = "other_attributes";
	
    //extertnal object
    public static final String ExternalObj_ID    = "UUID";
    public static final String ExternalObj_cTime = "creation_time";
    public static final String ExternalObj_mTime = "modification_time";
    public static final String ExternalObj_isDeleted = "is_deleted";
    
	// network properties and edges.
    public static final String Network_P_UUID       = ExternalObj_ID;
    public static final String Network_P_name       = "name";
    public static final String Network_P_visibility = "visibility";
    public static final String Network_P_isLocked   = "islocked";
    public static final String Network_P_isComplete = "iscomplete";
    public static final String Network_P_desc       = "description";
    public static final String Network_P_version    = "version";
    public static final String Network_P_nodeCount  = "nodecount";
    public static final String Network_P_edgeCount 	= "edgecount";
    public static final String Network_P_provenance = "provenance";
    public static final String Network_P_source_format = "sourceformat";  // Used internally. Will be convert to properties in the network model.
    public static final String Network_P_owner      = "owner";
    public static final String Network_P_metadata   = "aspectMetadata";
    public static final String Network_P_opaquEdgeTable = "opaqueAspects";
        
    //node
    public static final String Node_P_name         = "name";
    
    public static final String Node_P_represents   = "represents";
    public static final String Node_P_alias		   = "alias";
    public static final String Node_P_relatedTo	   = "relatedTo";
    
    // ndexProperty
    public static final String ndexProperties = "props";
    
    //Group
    public static final String GRP_P_NAME = "groupName";

    //user
    public static final String User_emailAddress   = "email_addr";
    public static final String User_verification_code = "verificationCode";
    public static final String User_firstName = "first_name";
    public static final String User_lastName = "last_name";
    public static final String User_isIndividual = "is_individual";
    public static final String User_displayName = "display_name";
    public static final String User_userName = "user_name";
    public static final String User_password = "password";
    public static final String User_isVerified = "is_verified";
        
    // task
    public static final String Task_P_description = "description";
    public static final String Task_P_status = "status";
    public static final String Task_P_priority = "priority";
    public static final String Task_P_progress = "progress";
    public static final String Task_P_taskType = "task_type";
    public static final String Task_P_resource = "resource";
    public static final String Task_P_fileFormat = "file_format";
    public static final String Task_P_startTime = "start_time";
    public static final String Task_P_endTime   = "end_time";
    public static final String Task_P_message   = "message";
    public static final String Task_P_attributes = "other_attributes";
    
    public static final String Task_E_owner   = "ownedBy";    
}

