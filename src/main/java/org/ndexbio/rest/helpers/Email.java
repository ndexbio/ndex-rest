package org.ndexbio.rest.helpers;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.helpers.Configuration;

public class Email
{
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
}
