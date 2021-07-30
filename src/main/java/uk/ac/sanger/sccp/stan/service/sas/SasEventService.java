package uk.ac.sanger.sccp.stan.service.sas;

import uk.ac.sanger.sccp.stan.model.*;

public interface SasEventService {
    /**
     * Records an SAS event.
     *
     * @param user      the user responsible for the event
     * @param sasNumber the sas number of the event
     * @param type      the type of event
     * @param comment   the reason for the event (may be null for some events)
     * @return the newly recorded event
     */
    SasEvent recordEvent(User user, SasNumber sasNumber, SasEvent.Type type, Comment comment);

    /**
     * Records an event that a sas number has changed status.
     * Some status changes require a comment id; some do not.
     * A sas number cannot be changed to the status it is already in.
     * A sas number that is closed cannot have its status changed.
     *
     * @param user      the user responsible for the change
     * @param sas       the sas number
     * @param newStatus the new status
     * @param commentId the id of the comment (if any) indicating the reason for the change
     * @return the newly recorded event
     */
    SasEvent recordStatusChange(User user, SasNumber sas, SasNumber.Status newStatus, Integer commentId);
}
