package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.StanFile;

import javax.persistence.EntityNotFoundException;
import java.util.List;

/** Repo for {@link StanFile} */
public interface StanFileRepo extends CrudRepository<StanFile, Integer> {
    /** Finds all active stan files associated with the given work id. */
    @Query("select f from StanFile f where f.work.id=?1 and f.deprecated is null")
    List<StanFile> findAllActiveByWorkId(Integer workId);

    /** Finds all active stan files with the given name, associated with the given work id. */
    @Query("select f from StanFile f where f.work.id=?1 and f.name=?2 and f.deprecated is null")
    List<StanFile> findAllActiveByWorkIdAndName(Integer workId, String name);

    /** Does a file exist with the given path? */
    boolean existsByPath(String path);

    /**
     * Gets the stan file with the given id
     * @param id the id of the stan file
     * @return the found stan file
     * @exception EntityNotFoundException if there is no stan file with the given id
     */
    default StanFile getById(Integer id) throws EntityNotFoundException {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("No stan file found with id "+id+"."));
    }
}
