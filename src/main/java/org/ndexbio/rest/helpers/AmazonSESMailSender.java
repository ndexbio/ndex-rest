package org.ndexbio.rest.helpers;

import java.util.Properties;
import java.util.concurrent.Semaphore;

import javax.mail.*;
import javax.mail.internet.*;

import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmazonSESMailSender {

	static Logger logger = LoggerFactory.getLogger(AmazonSESMailSender.class);

	private static AmazonSESMailSender INSTANCE = null;
	
	private AmazonSESMailSender () {
		mutex = new Semaphore(1);
		
		Configuration config = Configuration.getInstance();
		
	    FROM = config.getProperty("SMTP-From");  
	    SMTP_USERNAME = config.getProperty("SMTP-Username");
	    SMTP_PASSWORD = config.getProperty("SMTP-Password");
	    HOST = config.getProperty("SMTP-Host");
	    PORT = Integer.parseInt(config.getProperty("SMTP-Port"));
	}
	
	public static synchronized AmazonSESMailSender getInstance() {
		if (INSTANCE == null)
			INSTANCE = new AmazonSESMailSender();
		return INSTANCE;
	}

	private Semaphore mutex;
	
    private String FROM ;   // Replace with your "From" address. This address must be verified.
 //   static final String TO = "jic002@ucsd.edu";  // Replace with a "To" address. If your account is still in the 
                                                       // sandbox, this address must be verified.
    
 //   static final String BODY = "<H1>This email was sent through the Amazon SES SMTP interface by using Java.</H1> -cj";
 //   static final String SUBJECT = "Amazon SES test (SMTP interface accessed using Java)  -by ccj";
    
    // Supply your SMTP credentials below. Note that your SMTP credentials are different from your AWS credentials.
    private  String SMTP_USERNAME ;  // Replace with your SMTP username.
    private  String SMTP_PASSWORD ;  // Replace with your SMTP password.
    
    // Amazon SES SMTP host name. This example uses the US West (Oregon) region.
    private String HOST ;    
    
    // The port you will connect to on the Amazon SES SMTP endpoint. We are choosing port 25 because we will use
    // STARTTLS to encrypt the connection.
    private int PORT;

    //format should be 'plain' or 'html'
    public void sendEmail(String TO, String BODY, String SUBJECT, String format) throws Exception {

    	try {
    		  mutex.acquire();
    		  try {
    		    // do something
    			  
    		        // Create a Properties object to contain connection configuration information.
    		    	Properties props = System.getProperties();
    		    	props.put("mail.transport.protocol", "smtps");
    		    	props.put("mail.smtp.port", PORT); 
    		    	
    		    	// Set properties indicating that we want to use STARTTLS to encrypt the connection.
    		    	// The SMTP session will begin on an unencrypted connection, and then the client
    		        // will issue a STARTTLS command to upgrade to an encrypted connection.
    		    	props.put("mail.smtp.auth", "true");
    		    	props.put("mail.smtp.starttls.enable", "true");
    		    	props.put("mail.smtp.starttls.required", "true");

    		        // Create a Session object to represent a mail session with the specified properties. 
    		    	Session session = Session.getDefaultInstance(props);

    		        // Create a message with the specified information. 
    		        MimeMessage msg = new MimeMessage(session);
    		        msg.setFrom(new InternetAddress(FROM));
    		        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(TO));
    		        msg.setSubject(SUBJECT);
    		        msg.setContent(BODY,"text/" + format);
    		            
    		        // Create a transport.        
    		        Transport transport = session.getTransport();
    		                    
    		        // Send the message.
    		        try
    		        {
    		            System.out.println("Attempting to send an email through the Amazon SES SMTP interface...");
    		            
    		            // Connect to Amazon SES using the SMTP username and password you specified above.
    		            transport.connect(HOST, SMTP_USERNAME, SMTP_PASSWORD);
    		        	
    	
    		            // Send the email.
    		            transport.sendMessage(msg, msg.getAllRecipients());
    		            System.out.println("Email sent!");
    		        }
    		        catch (Exception ex) {
    		            System.out.println("The email was not sent.");
    		            System.out.println("Error message: " + ex.getMessage());
    		        }
    		        finally
    		        {
    		            // Close and terminate the connection.
    		            transport.close();        	
    		        }  
    			    Thread.sleep(100);
    			  
    		  } finally {
    		    mutex.release();
    		  }
    	} catch(InterruptedException ie) {
    		  logger.error("Email sender interrupted: " + ie.getMessage());
    	}
    	

    }
}