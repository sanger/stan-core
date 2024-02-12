package uk.ac.sanger.sccp.stan.service;

/**
 * Service for sending email notifications to admin users
 * @author dr6
 */
public interface AdminNotifyService {
    /**
     * Is the indicated admin notification enabled?
     * @param name the name of the notification
     * @return true if it is enabled; false if it is disabled
     */
    boolean isNotificationEnabled(String name);

    /**
     * Replaces {@code %service} with the name of the service, for clearer email messages.
     * @param string the string to modify
     * @return the modified string
     */
    String substitute(String string);

    /**
     * Issues the specified notification to admin users, if it is enabled.
     * {@link #substitute} is used on the {@code heading} and {@code body} arguments.
     * @param name the name of the notification
     * @param heading the email heading
     * @param body the text of the email
     * @return true if the email was sent; false otherwise
     */
    boolean issue(String name, String heading, String body);

}
