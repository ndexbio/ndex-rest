package org.ndexbio.server.tools;

import java.io.File;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.util.Util;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.helpers.AmazonSESMailSender;

public class UserStatsMailer implements AutoCloseable {
	

	private static final String dir = "/user_emails";
	
	
	private Connection db;
	
	private String prefix;
	
	private String dbLinkConnection; 
	
	private String lastMonth;
	
	
	private UserStatsMailer (String dbLink) throws SQLException {
		this.dbLinkConnection = dbLink;
		
		this.db = NdexDatabase.getInstance().getConnection();
		
		prefix = Configuration.getInstance().getNdexRoot() + dir;
		
		File directory = new File(prefix);

		if(!directory.exists()){
			 directory.mkdir();
		}
		
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd");  
		LocalDateTime now = LocalDateTime.now();  
		prefix += "/" +(dtf.format(now)); 
		
		directory = new File(prefix);

		if(directory.exists())
			directory.delete();

		directory.mkdir();
		prefix += "/";
		
		LocalDate previousMonth = LocalDate.now().minusMonths(1);
		
		lastMonth = previousMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.US) + " " + previousMonth.getYear();
	}
	
	
	// testonly: will not send emails out, only generate the emails in the file system.
	public void sendMailsToUsers(boolean testOnly) throws Exception {

		Set<UUID> topDownloads = getTopDownloadedNetworks();
		Map<String, Long> users = getUserList();
		
		for ( Map.Entry<String,Long> user : users.entrySet()) {
			sendEmailToUser(user, topDownloads, testOnly);
		}
		

	}
	
	// get the list of users that we need to send the email to.
	private Map<String,Long> getUserList() throws SQLException {
		Map<String,Long> users = new TreeMap<>();
		
		String sqlStr = "select * from dblink("+ dbLinkConnection + "::text, "
				+ "	'select owner,sum(downloads) from public_download_count "
				+ "where d_month = date_trunc(''month'',now()) group by owner') as t1 (owner text, total bigint)";

		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) 
					users.put(rs.getString(1), Long.valueOf(rs.getLong(2)));
			}
		} 
		
		
		
		return users;
	}
	
	// uuids of the top 20 downloaded networks of this month;
	private Set<UUID> getTopDownloadedNetworks () throws SQLException {
		String sqlStr = "select * from dblink("+ dbLinkConnection + "::text, "
				+ "	'select network_id from public_download_count where d_month = date_trunc(''month'',now()) order by downloads desc limit 20') as t1 (id uuid)";

		Set<UUID> topDownloads = new TreeSet<>();
		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) 
					topDownloads.add((UUID)rs.getObject(1));
			}
		} 
		
		return topDownloads;
	}
	
	
	private long getAlltimeDownloadCounts(String userName) throws SQLException, NdexException {
		String sqlStr = "select * from dblink("+ dbLinkConnection + "::text, "
				+ "	'select sum(downloads) from public_download_count where owner=''"+ userName + 
				"'' ') as t1 (cnt bigint)";

		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			try (ResultSet rs = st.executeQuery() ) {
				if(rs.next()) 
					return rs.getLong(1);
				throw new NdexException ("Empty result set when getting all time download counts.");
			}
		} 
		
	}
	
	private void sendEmailToUser(Map.Entry<String, Long> userEntry, Set<UUID> topDownloads, boolean testOnly) throws Exception {
		
		String emailTemplate = Util.readFile(Configuration.getInstance().getNdexRoot() + "/conf/Server_notification_email_template.html");

		String asterisk = "<strong style=\"color:red\"> * </Strong>";
		
		try (UserDAO dao = new UserDAO()){
			final User user = dao.getUserByAccountName(userEntry.getKey(),true,true);
			
			Boolean emailStats = user.getProperties() == null? null : ((Boolean)user.getProperties().get("emailUsageStats"));
			if ( emailStats!=null && !emailStats.booleanValue()) {
				System.out.println("User " + user.getUserName() + " disabled emailUsageStats setting.");
				return;
			}	
				
			String sqlStr = "select n.name, t1.id, t1.downloads from dblink("+  dbLinkConnection + "::text,\n" 
					+ "	 'select network_id,downloads from public_download_count where owner = ''" + user.getUserName() 
					+ "'' and d_month = date_trunc(''month'',now()) order by downloads desc limit 20') as t1 (id uuid, downloads bigint),\n"
					+ "	network n where n.\"UUID\" = t1.id";
			
			
			String messageBody = "Dear " + user.getUserName() + " account holder,<br>" + 
					"Thanks for using NDEx to publish your networks. Here are the usage statistics of your public networks in NDEx for the month of "
					+ lastMonth + ":<ul>"
					+ "<li>Number of downloads this month: <Strong>" + userEntry.getValue() + "</Strong></li>"
					+ "<li>Total number of downloads: <Strong>" + getAlltimeDownloadCounts(user.getUserName()) + "</Strong></li></ul>" +
					"Most downloaded networks in your account:<br>\n"
					+ "<table border=\"1\" cellpadding=\"2\" cellspacing=\"2\"><tr><th sytle=\"text-align:center;\">Network Name</th><th>Downloads</th><tr>";

			boolean hasTopDownloads = false;
			try (PreparedStatement st = db.prepareStatement(sqlStr)) {
				try (ResultSet rs = st.executeQuery() ) {
					while (rs.next()) {
						String networkName = rs.getString(1);
						UUID networkId = (UUID)rs.getObject(2);
						long cnt = rs.getLong(3);
						
						if ( topDownloads.contains(networkId) )
							hasTopDownloads = true;
						messageBody += "<tr><td>"+networkName + 
								(topDownloads.contains(networkId) ?  asterisk : "")
								+ "</td><td style=\"text-align:right\">" + cnt + "</td></tr>\n" ; 
						
					}
						
				}
			} 
			
			messageBody += "\n</table><br>";
			if ( hasTopDownloads) 
				messageBody += asterisk + " This network is one of the top 20 downloaded networks in NDEx.<br>";
				
			messageBody +=	"<br><strong>Notes - </strong>Download counts include these operations performed by other users:"
					+ "<ol><li>Views of your network through the NDEx web app</li>"
					+ "<li>Imports to Cyoscape desktop application</li><li>Usage from R, Python or other applications (i.e. IQuery, HiView, etc).</li></ol>"
					+ "<br>Thanks again for your contribution to the community. If you don't want to receive this monthly email in the future, "
					+ "please login to your <a href='https://www.ndexbio.org'>NDEx</a> account and disable this feature in your account page settings.";
			
			//System.out.print(messageBody);
			
			String fullEmail = emailTemplate.replaceFirst("%%____%%",messageBody);
			
			try (PrintWriter out = new PrintWriter(prefix + user.getUserName() + ".html")) {
			    out.println(fullEmail);
			}
			if ( !testOnly) {
				 AmazonSESMailSender.getInstance().sendEmail(user.getEmailAddress(), fullEmail,
						 "NDEx Notifications - Usage statistics of your public networks - " + lastMonth, "html");
				  //logger.info("Notified " + u.getUserName() + " one accepted request.");
				 System.out.println( "Stats sent to user " + user.getUserName() );
			}
		} 
		
		
	}
	
	
	
	
	
	@Override
	public void close () throws SQLException {
		db.close();
	}
	
	public static void main(String[] args) throws Exception {
		
		boolean testOnly = false;
		if (args.length == 1 && args[0].equals("test"))
			testOnly = true;
	//	SolrIndexBuilder i = new SolrIndexBuider();
		Configuration configuration = Configuration.createInstance();

		String statsDBConnStr = configuration.getStatsDBLink();
		if ( statsDBConnStr == null)
			throw new Exception ("DBLink for stats db is not defined in NDEx server config file.");
		
		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);
		
		
		try (UserStatsMailer mailer = new UserStatsMailer(statsDBConnStr)) {
		    mailer.sendMailsToUsers(testOnly);
		}
		
	 
	}

}
