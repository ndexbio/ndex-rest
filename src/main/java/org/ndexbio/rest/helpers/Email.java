/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
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
package org.ndexbio.rest.helpers;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.services.UserService;
import org.ndexbio.task.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Email
{
	
	static Logger logger = LoggerFactory.getLogger(Email.class);

    /**************************************************************************
    * Sends an email using configured settings for the SMTP server.
    *  
    * @param senderAddress
    *            The sender's email address.
    * @param recipientAddress
    *            The recipient's email address.
    * @param subject
    *            The email subject.
    * @param emailText
    *            The email content.
    * @throws MessagingException
    *            Failed to send the email.
     * @throws NdexException 
    **************************************************************************/
    public static void sendEmail(final String senderAddress, final String recipientAddress, final String subject, final String emailText) throws MessagingException, NdexException
    {
        sendEmail(senderAddress, new String[] { recipientAddress }, subject, emailText);
    }
    
    /**************************************************************************
    * Sends an email using configured settings for the SMTP server.
    *  
    * @param senderAddress
    *            The sender's email address.
    * @param recipientAddresses
    *            The recipient email addresses.
    * @param subject
    *            The email subject.
    * @param emailText
    *            The email content.
    * @throws MessagingException
    *            Failed to send the email.
     * @throws NdexException 
    **************************************************************************/
    public static void sendEmail(final String senderAddress, final String recipientAddresses[], final String subject, final String emailText) throws MessagingException, NdexException
    {
        //Dreamhost information can be found at: http://wiki.dreamhost.com/E-mail_Client_Configuration
        //Java headers can be found at: https://javamail.java.net/nonav/docs/api/com/sun/mail/smtp/package-summary.html
        final Properties smtpProperties = new Properties();
        smtpProperties.put("mail.smtp.auth", Configuration.getInstance().getProperty("SMTP-Auth"));
        smtpProperties.put("mail.smtp.host", Configuration.getInstance().getProperty("SMTP-Host"));
        smtpProperties.put("mail.smtp.port", Configuration.getInstance().getProperty("SMTP-Port"));

        sendEmail(senderAddress,
            recipientAddresses,
            subject,
            emailText,
            smtpProperties,
            Configuration.getInstance().getProperty("SMTP-Username"),
            Configuration.getInstance().getProperty("SMTP-Password"));
    }
    
    /**************************************************************************
    * Sends an email using the specified settings for the SMTP server.
    *  
    * @param senderAddress
    *            The sender's email address.
    * @param recipientAddresses
    *            The recipient email addresses.
    * @param subject
    *            The email subject.
    * @param emailText
    *            The email content.
    * @throws MessagingException
    *            Failed to send the email.
    **************************************************************************/
    public static void sendEmail(final String senderAddress, final String recipientAddresses[], final String subject, final String emailText, final Properties smtpProperties, final String smtpUsername, final String smtpPassword) throws MessagingException
    {
        Session smtpSession = Session.getInstance(smtpProperties,
            new javax.mail.Authenticator()
            {
                protected PasswordAuthentication getPasswordAuthentication()
                {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });
 
        final Message emailToSend = new MimeMessage(smtpSession);
        emailToSend.setFrom(new InternetAddress(senderAddress));
        emailToSend.setSubject(subject);
        emailToSend.setContent(emailText, "text/plain");
            
        for (String recipientAddress : recipientAddresses)
            emailToSend.addRecipient(Message.RecipientType.TO, InternetAddress.parse(recipientAddress)[0]);
 
        Transport.send(emailToSend);
    }
    
    public static void sendHTMLEmailUsingLocalhost(final String senderAddress, final String recipientAddress, final String subject, final String emailText ) throws NdexException {
	    // Get system properties
	        Properties properties = System.getProperties();

	    // Setup mail server
	    properties.setProperty("mail.smtp.host", "localhost");
	    
	    // Get the default Session object.
	      Session session = Session.getDefaultInstance(properties);
	    
	    try{
	          // Create a default MimeMessage object.
	          MimeMessage message = new MimeMessage(session);

	          // Set From: header field of the header.
	          message.setFrom(new InternetAddress(senderAddress));

	          // Set To: header field of the header.
	          message.addRecipient(Message.RecipientType.TO,
	                                   new InternetAddress(recipientAddress));

	          // Set Subject: header field
	          message.setSubject(subject);

	          // Now set the actual message
	          message.setText(emailText, "UTF-8", "html");

	          // Send message
	          Transport.send(message);
	          //System.out.println("Sent message successfully....");
	    }catch (MessagingException mex) {
	        throw new NdexException ("Failed to send email. Cause:" + mex.getMessage());
	    }
    }
    
}
