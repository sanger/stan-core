package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Some config related to emails
 * @author dr6
 */
@Configuration
public class MailConfig {
    @Value("${stan.mail.sender}")
    String sender;

    @Value("${stan.mail.alert_recipients}")
    String[] alertRecipients;

    @Value("${stan.mail.service_description}")
    String serviceDescription;

    @Value("${stan.mail.release_cc}")
    String releaseCC;

    /** The value to put in the "from" field of emails */
    public String getSender() {
        return this.sender;
    }

    /** The email addresses to send alert emails to */
    public String[] getAlertRecipients() {
        return this.alertRecipients;
    }

    /** A description of this service (e.g. <tt>"Stan UAT"</tt>) to include in emails. */
    public String getServiceDescription() {
        return this.serviceDescription;
    }

    /** The address to CC in release emails. */
    public String getReleaseCC() {
        return this.releaseCC;
    }
}
