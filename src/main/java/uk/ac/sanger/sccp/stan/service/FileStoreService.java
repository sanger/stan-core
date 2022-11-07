package uk.ac.sanger.sccp.stan.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.sanger.sccp.stan.model.StanFile;
import uk.ac.sanger.sccp.stan.model.User;

import java.util.List;

/** Service for helping to deal with stored files. */
public interface FileStoreService {
    /** Saves a file, creating a new entry in the files table. */
    StanFile save(User user, MultipartFile multipartFile, String workNumber);

    /** Loads the data for the given stan file */
    Resource loadResource(StanFile stanFile);

    /** Looks up the stan file record with the given id */
    StanFile lookUp(Integer id);

    /** Lists the active files linked to a particular work. */
    List<StanFile> list(String workNumber);
}
