package org.ndexbio.rest.helpers;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class Email
{
    /**************************************************************************
    * Sends an email using configured settings for the SMTP server.
    *  
    * @param senderAddress       The sender's email address.
    * @param recipientAddress    The recipient's email address.
    * @param subject             The email subject.
    * @param emailText           The email content.
    * @throws MessagingException Failed to send the email.
    **************************************************************************/
    public static void sendEmail(final String senderAddress, final String recipientAddress, final String subject, final String emailText) throws MessagingException
    {
        sendEmail(senderAddress, new String[] { recipientAddress }, subject, emailText);
    }
    
    /**************************************************************************
    * Sends an email using configured settings for the SMTP server.
    *  
    * @param senderAddress       The sender's email address.
    * @param recipientAddresses  The recipient email addresses.
    * @param subject             The email subject.
    * @param emailText           The email content.
    * @throws MessagingException Failed to send the email.
    **************************************************************************/
    public static void sendEmail(final String senderAddress, final String recipientAddresses[], final String subject, final String emailText) throws MessagingException
    {
        //TODO: All of this should be pulled from a configuration file
        final Properties smtpProperties = new Properties();
        smtpProperties.put("mail.smtp.auth", "true");
        smtpProperties.put("mail.smtp.starttls.enable", "true");
        smtpProperties.put("mail.smtp.host", "smtp.gmail.com");
        smtpProperties.put("mail.smtp.port", "587");

        sendEmail(senderAddress, recipientAddresses, subject, emailText, smtpProperties, "dexterpratt.bio@gmail.com", "insecure");
    }
    
    /**************************************************************************
    * Sends an email using the specified settings for the SMTP server.
    *  
    * @param senderAddress       The sender's email address.
    * @param recipientAddresses  The recipient email addresses.
    * @param subject             The email subject.
    * @param emailText           The email content.
    * @throws MessagingException Failed to send the email.
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
        emailToSend.setText(emailText);
            
        for (String recipientAddress : recipientAddresses)
            emailToSend.addRecipient(Message.RecipientType.TO, InternetAddress.parse(recipientAddress)[0]);
 
        Transport.send(emailToSend);
    }
}
