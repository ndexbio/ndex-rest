package org.ndexbio.rest;

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
import org.ndexbio.common.models.dao.orientdb.GroupDocDAO;
import org.ndexbio.common.models.dao.orientdb.NetworkDocDAO;
import org.ndexbio.common.models.dao.orientdb.RequestDAO;
import org.ndexbio.common.models.dao.orientdb.UserDocDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.ResponseType;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.helpers.Email;
import org.ndexbio.task.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

public class EmailNotificationTask extends TimerTask {

	//expressed in milliseconds
	protected final static long fONCE_PER_DAY = 1000*60*60*24;

	private static Logger logger = LoggerFactory.getLogger(EmailNotificationTask.class);
	
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

	public EmailNotificationTask() {
	}

	@Override
	public void run() {
	
	  logger.info("Performing email notification tasks.");
		// testing the email notification code.
	  try (UserDocDAO userdao = new UserDocDAO()) {
			
		Map<String, Map<ResponseType, Integer>> tab = getNotificationTable(userdao.getDBConnection());
		
		String senderAddress = Configuration.getInstance().getProperty("Feedback-Email");
		String emailSubject = "NDEx Notifications - ";
		
		for ( Map.Entry<String, Map<ResponseType,Integer>> rec : tab.entrySet()) {
			String userUUIDStr = rec.getKey();
			User u = userdao.getUserById(UUID.fromString(userUUIDStr));
			Map<ResponseType,Integer> notifications = rec.getValue();
			if ( notifications.get(ResponseType.PENDING)!=null)	{			
			
				String emailString = "Dear " + u.getAccountName() + " account holder,\n\n" + 
					"You have received one or more requests to access networks or groups that you currently manage in NDEx. \n" + 
					"Please log in to your account to review and manage all pending requests.\n" + 
					"This is an automated message, please do not respond to this email. If you need help, contact us by emailing: support@ndexbio.org\n\n" +
					"Best Regards,\n" + 
					"The NDEx team\n";
				
				// send email;
				Email.sendEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Have Pending Request(s)",
						emailString);
			}
			if ( notifications.get(ResponseType.ACCEPTED)!=null)	{			
				
				String emailString = "Dear " + u.getAccountName() + " account holder,\n\n" + 
					"Your pending requests have been reviewed by the account's administrator.\n"+
					"You can now log in to your account and access new networks and groups.\n" +
					"This is an automated message, please do not respond to this email. If you need help, contact us by emailing: support@ndexbio.org\n\n" +
					"Best Regards,\n" + 
					"The NDEx team\n";
				
				// send email;
				Email.sendEmailUsingLocalhost(senderAddress, u.getEmailAddress(), emailSubject + "You Request Has Been Reviewed",
						emailString);
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
	private static Map<String, Map<ResponseType, Integer>> getNotificationTable(ODatabaseDocumentTx dbconn) throws NdexException{
		  		
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
	  						GroupDocDAO grpdao = new GroupDocDAO ( dbconn);
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
