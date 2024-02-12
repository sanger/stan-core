package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Map;

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

    private UCMap<Boolean> adminNotifications;

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

    public boolean isAdminNotificationEnabled(String name) {
        return this.adminNotifications.getOrDefault(name, Boolean.TRUE);
    }

    @Autowired
    public void setAdminNotifications(@Value("#{${stan.mail.admin_notify}}") Map<String, Boolean> notifications) {
        this.adminNotifications = (notifications==null ? new UCMap<>(0) : new UCMap<>(notifications));
    }
}
