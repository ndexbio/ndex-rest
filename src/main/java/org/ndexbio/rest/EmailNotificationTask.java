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
package org.ndexbio.rest;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.UUID;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDocDAO;
import org.ndexbio.common.models.dao.postgresql.RequestDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.ResponseType;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.helpers.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailNotificationTask extends TimerTask {

	//expressed in milliseconds
	protected final static long fONCE_PER_DAY = 1000*60*60*24;

	private static Logger logger = LoggerFactory.getLogger(EmailNotificationTask.class);
	
	private String emailTemplate;
	
   private final static int f12_AM = 0;

	  protected static Date getTomorrowNotificationTime(){
	    Calendar tomorrow = new GregorianCalendar();
	    tomorrow.add(Calendar.DATE, 1);
	    Calendar result = new GregorianCalendar(
	      tomorrow.get(Calendar.YEAR),
	      tomorrow.get(Calendar.MONTH),
	      tomorrow.get(Calendar.DATE),
	      f12_AM,
	      0
	    );
	    return result.getTime();
	  }	

	public EmailNotificationTask() throws IOException, NdexException {
	 emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");

	}

	@Override
	public void run() {
	
	  logger.info("Performing email notification tasks.");
		// testing the email notification code.
	  try (UserDAO userdao = new UserDAO()) {
			
		Map<String, Map<ResponseType, Integer>> tab = getNotificationTable();
		
		String senderAddress = Configuration.getInstance().getProperty("Feedback-Email");
		String emailSubject = "NDEx Notifications - ";
		
		for ( Map.Entry<String, Map<ResponseType,Integer>> rec : tab.entrySet()) {
			String userUUIDStr = rec.getKey();
			User u = userdao.getUserById(UUID.fromString(userUUIDStr));
			Map<ResponseType,Integer> notifications = rec.getValue();
			if ( notifications.get(ResponseType.PENDING)!=null)	{			
			
				String emailString = "Dear " + u.getAccountName() + " account holder,<br>" + 
					"You have received one or more requests to access networks or groups that you currently manage in NDEx. " + 
					"Please log in to your account to review and manage all pending requests." ;
					
				
				// send email;
				Email.sendHTMLEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Have Pending Request(s)",
					  emailTemplate.replaceFirst("%%____%%",emailString));
			}
			if ( notifications.get(ResponseType.ACCEPTED)!=null)	{			
				
				String emailString = "Dear " + u.getAccountName() + " account holder,<br>" + 
					"Your pending requests have been reviewed by the account's administrator. "+
					"You can now log in to your account and access new networks and groups." ;
				
				// send email;
				Email.sendHTMLEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Request Has Been Reviewed",
						emailTemplate.replaceFirst("%%____%%",emailString));
			}
			
		}
		
	  } catch (NdexException e) {
		logger.error("Error occured when sending email notifications. Cause:" + e.getMessage() );
		e.printStackTrace();
	} 

	}
	
	/**
	 * Generates a table from db that stores the user uuids and their pending/accepted/denied request within the last 24 hours.
	 * @return
	 * @throws NdexException 
	 */
	private static Map<String, Map<ResponseType, Integer>> getNotificationTable() throws NdexException{
		  		
	  			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
	  						"SELECT FROM " + NdexClasses.Request +
	  						" WHERE sysdate().asLong()  - modificationTime.asLong()  < 24*3600000 and  isDeleted=false"
	  								 );

	  			List<ODocument> records = dbconn.command(query).execute();

	  		    Map <String, Map<ResponseType, Integer>> result = new HashMap<> ();
	  			for (ODocument request : records) {
	  				Request r = RequestDAO.getRequestFromDocument(request);
	  				if ( r.getResponse() == ResponseType.PENDING ) {  // pending request need to notify the destinations side.
	  					if (r.getPermission() == Permissions.MEMBER || r.getPermission() == Permissions.GROUPADMIN ) {
	  						// group request. need to notify all admins of the group
	  						GroupDAO grpdao = new GroupDAO ( dbconn);
	  						List<Membership> members = grpdao.getGroupUserMemberships(r.getDestinationUUID(), Permissions.GROUPADMIN, 0, 5);
	  						for ( Membership member : members){
	  							String uuidStr = member.getMemberUUID().toString();
	  							Map<ResponseType, Integer> notifications = result.get(uuidStr);
	  							if ( notifications == null) {
	  								notifications = new HashMap<>();
	  								result.put(uuidStr, notifications);
	  							}  
	  							Integer cnt = notifications.get(r.getResponse());
	  							if ( cnt == null)
	  								cnt = 1;
	  							else 
	  								cnt = cnt + 1;
	  							notifications.put(ResponseType.PENDING, cnt);
	  						}
	  					} else {
	  						// network request. need to notify all admins of the network
	  						NetworkDocDAO networkdao = new NetworkDocDAO (dbconn);
	  						Set<String> uuids = networkdao.getAdminUsersOnNetwork(r.getDestinationUUID().toString());
	  						
	  				//		UserDocDAO userdao = new UserDocDAO ( dbconn);
	  						for ( String userUUIDStr : uuids){
	  							Map<ResponseType, Integer> notifications = result.get(userUUIDStr);
	  							if ( notifications == null) {
	  								notifications = new HashMap<>();
	  								result.put(userUUIDStr, notifications);
	  							}  
	  							Integer cnt = notifications.get(r.getResponse());
	  							if ( cnt == null)
	  								cnt = 1;
	  							else 
	  								cnt = cnt + 1;
	  							notifications.put(ResponseType.PENDING, cnt);
	  						}
	  						
	  					}
	  					
	  				} else { //accepted or denied request need to notify the source 
						Map<ResponseType, Integer> notifications = result.get(r.getSourceUUID().toString());
						if ( notifications == null) {
							notifications = new HashMap<>();
						    result.put(r.getSourceUUID().toString(), notifications);						
						}
	  					Integer cnt = notifications.get(ResponseType.ACCEPTED);
	  					if ( cnt == null) 
	  						cnt = 1;
	  					else 
	  						cnt = cnt+1;
	  					notifications.put(ResponseType.ACCEPTED, cnt);
	  				}
	  				
	  			}
	  		return result;	
	}
	


}
