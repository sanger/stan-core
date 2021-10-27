package uk.ac.sanger.sccp.stan.service.work;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.Collection;
import java.util.Map;

public interface WorkEventService {
    /**
     * Records a work event.
     *
     * @param user      the user responsible for the event
     * @param work      the work of the event
     * @param type      the type of event
     * @param comment   the reason for the event (may be null for some events)
     * @return the newly recorded event
     */
    WorkEvent recordEvent(User user, Work work, WorkEvent.Type type, Comment comment);

    /**
     * Records an event that a work number has changed status.
     * Some status changes require a comment id; some do not.
     * A work cannot be changed to the status it is already in.
     * A work that is closed cannot have its status changed.
     *
     * @param user      the user responsible for the change
     * @param work      the work
     * @param newStatus the new status
     * @param commentId the id of the comment (if any) indicating the reason for the change
     * @return the newly recorded event
     */
    WorkEvent recordStatusChange(User user, Work work, Work.Status newStatus, Integer commentId);

    /**
     * Gets the latest work event for each of the given work ids
     * @param workIds the work ids
     * @return a map of work id to event
     */
    Map<Integer, WorkEvent> loadLatestEvents(Collection<Integer> workIds);
}
