package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Comment;

import java.util.*;

/**
 * @author dr6
 */
public interface CommentRepo extends CrudRepository<Comment, Integer> {
    List<Comment> findAllByCategoryAndEnabled(String category, boolean enabled);

    @Query("select id from Comment where id in (?1)")
    Set<Integer> findIdByIdIn(Collection<Integer> ids);
}
