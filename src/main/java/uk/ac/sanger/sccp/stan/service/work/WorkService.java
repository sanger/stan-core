package uk.ac.sanger.sccp.stan.service.work;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.request.SuggestedWorkResponse;
import uk.ac.sanger.sccp.stan.request.WorkWithComment;
import uk.ac.sanger.sccp.utils.UCMap;

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
     * @param workRequesterName the name of the ReleaseRecipient requesting the work
     * @param projectName the name of the project for the work
     * @param programName the name of the program for the work
     * @param costCode the code of the cost code for the work
     * @param numBlocks the value for the "numBlocks" field (may be null)
     * @param numSlides the value for the "numSlides" field (may be null)
     * @param numOriginalSamples the value for the "numOriginalSamples" field (may be null)
     * @param omeroProjectName the name of the omero project for this work (may be null)
     * @return the new work
     */
    Work createWork(User user, String prefix, String workTypeName, String workRequesterName, String projectName, String programName, String costCode,
                    Integer numBlocks, Integer numSlides, Integer numOriginalSamples, String omeroProjectName);

    /**
     * Updates the status of the work. Records a work event for the change.
     * The comment id is required for changes that need a reason
     * (i.e. pause, fail and withdraw); and must be null for changes that
     * do not need a reason.
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
     * Updates the numOriginalSamples field on an existing work.
     * @param user the user responsible for the change
     * @param workNumber the work number of an existing work
     * @param numOriginalSamples the new value of numOriginalSamples (may be null)
     * @return the updated work
     */
    Work updateWorkNumOriginalSamples(User user, String workNumber, Integer numOriginalSamples);

    /**
     * Updates the priority field on an existing work.
     * @param user the user responsible for the change
     * @param workNumber the work number of an existing work
     * @param priority the new value of priority (may be null)
     * @return the updated work
     */
    Work updateWorkPriority(User user, String workNumber, String priority);

    /**
     * Updates the omero project of an existing work.
     * @param user the user responsible
     * @param workNumber the work number of the work
     * @param omeroProjectName the name of an existing omero project; or null to clear the project
     * @return the updated work
     */
    Work updateWorkOmeroProject(User user, String workNumber, String omeroProjectName);

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
     * Updates the given works linking them to the given operations and samples in slots in the ops' actions
     * @param works the works
     * @param operations the operations to link
     * @exception IllegalArgumentException if any of the works is not active
     */
    void link(Collection<Work> works, Collection<Operation> operations);

    /**
     * Updates the given work linking it to the given releases and samples in the released labware
     * @param work the work to link the release to
     * @param releases the releases to link to the work
     * @return the updated work
     * @exception IllegalArgumentException if the work is not usable
     */
    Work linkReleases(Work work, List<Release> releases);

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
     * Gets the specified works and checks they are usable.
     * Errors if any of the given work numbers is null, or invalid, or indicates an unusable work
     * @param workNumbers the work numbers
     * @return a map of works from their work numbers
     * @exception javax.persistence.EntityNotFoundException if any work number is unrecognised
     * @exception IllegalArgumentException if any work is unusable
     * @exception NullPointerException if any given work number is null
     */
    UCMap<Work> getUsableWorkMap(Collection<String> workNumbers);

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
     * Validates the specified work as usable.
     * If any of the work doesn't exist or cannot be used, adds a problem to the given problems receptacle.
     * @param problems a receptacle for any problems found
     * @param workNumbers the strings representing existing work numbers
     * @return a map of the work numbers to the specified work (even if it is not usable)
     * @see Work#isUsable
     */
    UCMap<Work> validateUsableWorks(Collection<String> problems, Collection<String> workNumbers);

    /**
     * Loads works along with the comment about their status, if any.
     * Optional filtered by work status.
     * @param workStatuses the statuses to filter by (may be null, in which case unfiltered)
     * @return a list of WorkWithComment objects each of which may or may not include a comment
     */
    List<WorkWithComment> getWorksWithComments(Collection<Work.Status> workStatuses);

    /**
     * Gets the suggested works for the indicated labware.
     * For each barcode, gives the latest (if any) active work that the labware was used in.
     * @param barcodes barcodes of labware
     * @param includeInactive true to include inactive work numbers in the results
     * @return the suggested works
     */
    SuggestedWorkResponse suggestWorkForLabwareBarcodes(Collection<String> barcodes, boolean includeInactive);

    /**
     * Gets the labware last associated with the specified work.
     * @param workNumber an existing work number
     * @param forRelease true if to suggest releasable labware rather than usable labware
     * @return the list of labware whose last work is the given work
     * @exception javax.persistence.EntityNotFoundException if the work number is not found
     */
    List<Labware> suggestLabwareForWorkNumber(String workNumber, boolean forRelease);

    /**
     * Gets the works created by the given user.
     * @param user the user who created the works
     * @return the works created by the user
     */
    List<Work> getWorksCreatedBy(User user);
}
