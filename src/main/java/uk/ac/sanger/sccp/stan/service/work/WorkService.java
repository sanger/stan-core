package uk.ac.sanger.sccp.stan.service.work;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.request.WorkWithComment;

import java.util.Collection;
import java.util.List;

/**
 * Service for managing {@link Work work}.
 * @author dr6
 */
public interface WorkService {
    /**
     * Creates a new work. Records a create event for the work linked to the specified user
     * @param user the user responsible for creating the work
     * @param prefix the prefix ({@code SGP} or {@code R&D}) for the work
     * @param workTypeName the name of a work type for the work number
     * @param projectName the name of the project for the work number
     * @param costCode the code of the cost code for the work number
     * @param numBlocks the value for the "numBlocks" field (may be null)
     * @param numSlides the value for the "numSlides" field (may be null)
     * @return the new work
     */
    Work createWork(User user, String prefix, String workTypeName, String projectName, String costCode,
                    Integer numBlocks, Integer numSlides);

    /**
     * Updates the status of the work. Records a work event for the change.
     * The comment id is required for changes that need a reason (i.e. pause and fail); and must be null
     * for changes that do not need a reason.
     * @param user the user responsible for the change
     * @param workNumber the work to be updated
     * @param newStatus the new status
     * @param commentId the id of the comment giving a reason for the change
     * @return the updated work along with the comment, if any
     */
    WorkWithComment updateStatus(User user, String workNumber, Status newStatus, Integer commentId);

    /**
     * Updates the numBlocks field on an existing work.
     * @param user the user responsible for the change
     * @param workNumber the work number of an existing work
     * @param numBlocks the new value of numBlocks (may be null)
     * @return the updated work
     */
    Work updateWorkNumBlocks(User user, String workNumber, Integer numBlocks);

    /**
     * Updates the numSlides field on an existing work.
     * @param user the user responsible for the change
     * @param workNumber the work number of an existing work
     * @param numSlides the new value of numSlides (may be null)
     * @return the updated work
     */
    Work updateWorkNumSlides(User user, String workNumber, Integer numSlides);

    /**
     * Updates the existing work linking it to the given operations and samples in slots in the ops' actions
     * @param workNumber the string identifying an existing work
     * @param operations the operations to link
     * @return the updated and saved work
     * @exception javax.persistence.EntityNotFoundException if the work does not exist
     * @exception IllegalArgumentException if the work is not active
     */
    Work link(String workNumber, Collection<Operation> operations);

    /**
     * Updates the existing work linking it to the given operations and samples in slots in the ops' actions
     * @param work the work
     * @param operations the operations to link
     * @return the updated and saved work
     * @exception IllegalArgumentException if the work is not active
     */
    Work link(Work work, Collection<Operation> operations);

    /**
     * Gets the specified work.
     * Errors if the work number cannot be used.
     * @param workNumber the string representing an existing work
     * @return the active work corresponding to the argument
     * @see Work#isUsable
     * @exception javax.persistence.EntityNotFoundException if the work number is unrecognised
     * @exception IllegalArgumentException if the work is not usable
     * @exception NullPointerException if the given string is null
     */
    Work getUsableWork(String workNumber);

    /**
     * Validates the specified work as usable.
     * If the work doesn't exist or cannot be used, adds a problem to the given problems receptacle.
     * Just returns null if the given string is null.
     * @param problems a receptacle for any problems found
     * @param workNumber the string representing an existing work number
     * @return the active work corresponding to the given string
     * @see Work#isUsable
     */
    Work validateUsableWork(Collection<String> problems, String workNumber);

    /**
     * Loads works along with the comment about their status, if any.
     * Optional filtered by work status.
     * @param workStatuses the statuses to filter by (may be null, in which case unfiltered)
     * @return a list of WorkWithComment objects each of which may or may not include a comment
     */
    List<WorkWithComment> getWorksWithComments(Collection<Work.Status> workStatuses);
}
