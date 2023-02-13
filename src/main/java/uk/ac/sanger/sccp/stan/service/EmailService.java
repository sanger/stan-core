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

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Service for sending simple emails
 * @author dr6
 */
@Service
public class EmailService {
    Logger log = LoggerFactory.getLogger(StoreService.class);

    private final JavaMailSender mailSender;
    private final MailConfig mailConfig;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
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
     * @param cc cc for the email (or null)
     * @exception MailException if the message failed to send
     */
    public void send(String subject, String text, String[] recipients, String[] cc) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipients);
        if (cc!=null) {
            message.setCc(cc);
        }
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
            send(subject, text, recipients, null);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email.", e);
            return false;
        }
    }

    /**
     * A description of this service (e.g. "Stan UAT") for inclusion in emails
     * @return a description of this service
     */
    public String getServiceDescription() {
        return mailConfig.getServiceDescription();
    }

    /**
     * Tries to send a release email.
     * @param recipient the recipient of the release email
     * @param releaseFilePath the path to download the release file
     * @return true if the email was sent successfully; false if it was not
     */
    public boolean tryReleaseEmail(String recipient, String releaseFilePath) {
        String[] recipients = new String[] {recipient};
        String releaseCc = mailConfig.getReleaseCC();
        String[] cc;
        if (nullOrEmpty(releaseCc)) {
            cc = null;
        } else {
            if (releaseCc.indexOf('@') < 0) {
                releaseCc += "@sanger.ac.uk";
            }
            cc = new String[] { releaseCc };
        }
        String subject = mailConfig.getServiceDescription()+" release";
        String text = "The details of the release are available at "+releaseFilePath;
        try {
            send(subject, text, recipients, cc);
            return true;
        } catch (Exception e) {
            log.error("Failed to send release email.", e);
            return false;
        }
    }
}
