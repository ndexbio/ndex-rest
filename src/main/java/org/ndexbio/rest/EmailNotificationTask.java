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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
import java.util.UUID;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.ResponseType;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.helpers.AmazonSESMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class EmailNotificationTask extends TimerTask {

	//expressed in milliseconds
	protected final static long fONCE_PER_DAY = 1000*60*60*24;

	private static Logger logger = LoggerFactory.getLogger(EmailNotificationTask.class);
	
	private String emailTemplate;
	
   private final static int f12_AM = 0;
   
   private Exception error;

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

	public EmailNotificationTask() throws IOException {
	 emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");
	 error = null;
	}

	@Override
	public void run() {
	
	  logger.info("Performing email notification tasks.");
		// testing the email notification code.
	  try (UserDAO userdao = new UserDAO()) {
			
		Map<UUID, Map<ResponseType, Integer>> tab = getNotificationTable();
		
	//	String senderAddress = Configuration.getInstance().getProperty("Feedback-Email");
		String emailSubject = "NDEx Notifications - ";
		
		for ( Map.Entry<UUID, Map<ResponseType,Integer>> rec : tab.entrySet()) {
			UUID userUUID = rec.getKey();
			User u = userdao.getUserById(userUUID,true,true);
			Map<ResponseType,Integer> notifications = rec.getValue();
			if ( notifications.get(ResponseType.PENDING)!=null)	{			
			
				String emailString = "Dear " + u.getUserName() + " account holder,<br>" + 
					"You have received one or more requests to access networks or groups that you currently manage in NDEx. " + 
					"Please log in to your account to review and manage all pending requests." ;
					
				
				// send email;
				 AmazonSESMailSender.getInstance().sendEmail(u.getEmailAddress(), emailTemplate.replaceFirst("%%____%%",emailString),
						 emailSubject + "You Have Pending Request(s)" , "html");
				 
				  logger.info("Notified " + u.getUserName() + " one pending request.");

				/*Email.sendHTMLEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Have Pending Request(s)",
					  emailTemplate.replaceFirst("%%____%%",emailString)); */
			}
			if ( notifications.get(ResponseType.ACCEPTED)!=null)	{			
				
				String emailString = "Dear " + u.getUserName() + " account holder,<br>" + 
					"Your pending requests have been reviewed by the account's administrator. "+
					"You can now log in to your account and access new networks and groups." ;
				
				// send email;
				 AmazonSESMailSender.getInstance().sendEmail(u.getEmailAddress(), emailTemplate.replaceFirst("%%____%%",emailString),
						 emailSubject + "Your Request Has Been Reviewed" , "html");
				  logger.info("Notified " + u.getUserName() + " one accepted request.");

			/*	Email.sendHTMLEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Request Has Been Reviewed",
						emailTemplate.replaceFirst("%%____%%",emailString)); */
			}
			
		}
		
	  } catch (NdexException e) {
		logger.error("Error occurred when sending email notifications. Cause:" + e.getMessage() );
		e.printStackTrace();
		setError(e);
	} catch (SQLException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
		setError(e1);

	} catch (JsonParseException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (JsonMappingException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IllegalArgumentException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} 

	}
	
	/**
	 * Generates a table from db that stores the user uuids and their pending/accepted/denied request within the last 24 hours.
	 * @return
	 * @throws NdexException 
	 */
	@SuppressWarnings("boxing")
	private static Map<UUID, Map<ResponseType, Integer>> getNotificationTable() throws NdexException{
		  		
	/*  			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
	  						"SELECT FROM " + NdexClasses.Request +
	  						" WHERE sysdate().asLong()  - modificationTime.asLong()  < 24*3600000 and  isDeleted=false"
	  								 );

	  			List<ODocument> records = dbconn.command(query).execute(); */

	  		    Map <UUID, Map<ResponseType, Integer>> result = new HashMap<> ();
	  		    
	  			try (Connection db = NdexDatabase.getInstance().getConnection() ) {
	  				String sql = "select destinationuuid, request_type, response,owner_id from request "
	  						+ "where current_timestamp - modification_time < interval '1 day'";
	  				
	  				try (PreparedStatement st = db.prepareStatement(sql))  {
	  					try (ResultSet rs = st.executeQuery() ) {
	  						while (rs.next()) {
	  							String requestType = rs.getString(2);
  								UUID destUUID = (UUID)rs.getObject(1);
  								ResponseType responseType = ResponseType.valueOf(rs.getString(3));
  								if ( responseType.equals(ResponseType.PENDING)) {  // notify the recipient.
  									if ( requestType.equals("JoinGroup")) {
	  								try (GroupDAO dao = new GroupDAO()) {
	  									for (UUID userid :  dao.getGroupAdminIds(destUUID)) {
	  										Map<ResponseType, Integer> notifications = result.get(userid);
	  			  							if ( notifications == null) {
	  			  								notifications = new HashMap<>();
	  			  								result.put(userid, notifications);
	  			  							}  
	  			  							Integer cnt = notifications.get(ResponseType.PENDING);
	  			  							if ( cnt == null)
	  			  								cnt = 1;
	  			  							else 
	  			  								cnt = cnt + 1;
	  			  							notifications.put(ResponseType.PENDING, cnt);
	  									}
	  								}
	  								
  									} else {    // network permission request, notify network admin
	  								try (NetworkDAO dao = new NetworkDAO()) {
	  									UUID userid = dao.getNetworkOwner(destUUID);
	  									Map<ResponseType, Integer> notifications = result.get(userid);
  			  							if ( notifications == null) {
  			  								notifications = new HashMap<>();
  			  								result.put(userid, notifications);
  			  							}  
  			  							Integer cnt = notifications.get(ResponseType.PENDING);
  			  							if ( cnt == null)
  			  								cnt = 1;
  			  							else 
  			  								cnt = cnt + 1;
  			  							notifications.put(ResponseType.PENDING, cnt);
	  								}
  									}
  								} else { //notify the requester
  									UUID ownerId = (UUID) rs.getObject(4);
  									Map<ResponseType, Integer> notifications = result.get(ownerId);
			  						if ( notifications == null) {
			  								notifications = new HashMap<>();
			  								result.put(ownerId, notifications);
			  						}  
			  						Integer cnt = notifications.get(responseType);
			  						if ( cnt == null)
			  								cnt = 1;
			  						else 
			  								cnt = cnt + 1;
			  						notifications.put(responseType, cnt);
  								}
	  						} 
	  					}
	  				}	
	  				
	  			} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

	  		    
	  		return result;	
	}

	public Exception getError() {
		return error;
	}

	public void setError(Exception error) {
		this.error = error;
	}
	


}
