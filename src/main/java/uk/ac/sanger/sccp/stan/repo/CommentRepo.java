package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Comment;

import javax.persistence.EntityNotFoundException;
import java.util.*;

/**
 * @author dr6
 */
public interface CommentRepo extends CrudRepository<Comment, Integer> {
    List<Comment> findAllByCategory(String category);
    List<Comment> findAllByCategoryAndEnabled(String category, boolean enabled);
    Optional<Comment> findByCategoryAndText(String category, String text);

    @Query("select id from Comment where id in (?1)")
    Set<Integer> findIdByIdIn(Collection<Integer> ids);

    List<Comment> findAllByIdIn(Collection<Integer> ids);

    List<Comment> findAllByEnabled(boolean enabled);

    default Comment getById(int id) throws EntityNotFoundException {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("No comment found with id "+id));
    }
}
