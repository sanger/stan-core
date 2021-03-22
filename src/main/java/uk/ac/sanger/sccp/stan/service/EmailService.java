package uk.ac.sanger.sccp.stan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.config.MailConfig;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

/**
 * Service for sending simple emails
 * @author dr6
 */
@Service
public class EmailService {
    Logger log = LoggerFactory.getLogger(StoreService.class);

    private final JavaMailSender mailSender;
    private final MailConfig mailConfig;

    @Autowired
    public EmailService(JavaMailSender mailSender, MailConfig mailConfig) {
        this.mailSender = mailSender;
        this.mailConfig = mailConfig;
    }

    /**
     * Sends an email
     * @param subject the subject of the email
     * @param text the text of the email
     * @param recipients the recipients
     * @exception MailException if the message failed to send
     */
    public void send(String subject, String text, String[] recipients) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipients);
        message.setFrom(mailConfig.getSender());
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    /**
     * Tries to send an email to the alert recipients (listed in config)
     * @param subject the subject of the email
     * @param text the text of the email
     * @return true if the email was successfully sent; false if there was an exception
     */
    public boolean tryAndSendAlert(String subject, String text) {
        String[] recipients = mailConfig.getAlertRecipients();
        if (recipients==null || recipients.length==0) {
            return false;
        }
        try {
            send(subject, text, recipients);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email.", e);
            return false;
        }
    }
}
