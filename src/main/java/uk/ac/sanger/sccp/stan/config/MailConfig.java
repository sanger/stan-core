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

    public String getSender() {
        return this.sender;
    }

    public String[] getAlertRecipients() {
        return this.alertRecipients;
    }
}
