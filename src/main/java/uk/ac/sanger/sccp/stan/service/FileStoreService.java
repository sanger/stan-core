package uk.ac.sanger.sccp.stan.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.model.StanFile;
import uk.ac.sanger.sccp.stan.model.User;

import java.util.List;

/** Service for helping to deal with stored files. */
public interface FileStoreService {
    /**
     * Saves a file, creating a new entry in the files table (with an internal transaction).
     * @param user user uploading the file
     * @param multipartFile the file data
     * @param workNumber the work number to save the file in association with
     * @return a new stanfile from the database
     * @exception java.io.UncheckedIOException if saving the file causes an IOException
     * @exception javax.persistence.EntityNotFoundException if referenced entities do not exist
     */
    StanFile save(User user, MultipartFile multipartFile, String workNumber);

    /**
     * Loads the data for the given stan file
     * @param stanFile an existing StanFile object
     * @return the resource of the indicated file
     * @exception IllegalStateException if the indicated is deprecated (i.e. replaced by a newer version)
     * @exception java.io.UncheckedIOException if opening the file causes an IOException
     */
    Resource loadResource(StanFile stanFile);

    /**
     * Looks up the stan file record with the given id
     * @param id the id of a stan file
     * @return the stan file with the given id
     * @exception javax.persistence.EntityNotFoundException if the indicated stan file is not found
     */
    StanFile lookUp(Integer id);

    /**
     * Lists the active files linked to a particular work.
     * @param workNumber the work number of an existing work
     * @return the stan file objects associated with the given work number
     * @exception javax.persistence.EntityNotFoundException if the indicated work cannot be found
     */
    List<StanFile> list(String workNumber);
}
