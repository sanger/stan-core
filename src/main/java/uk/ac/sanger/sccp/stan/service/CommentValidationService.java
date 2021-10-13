package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Comment;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Service to validate comment IDs
 * @author dr6
 */
@Service
public class CommentValidationService {
    private final CommentRepo commentRepo;

    @Autowired
    public CommentValidationService(CommentRepo commentRepo) {
        this.commentRepo = commentRepo;
    }

    /**
     * Validates some comment ids.
     * If the comment ids are optional, omit the null elements before you call this method.
     * Possible problems include:<ul>
     *     <li>Null included as a comment id</li>
     *     <li>Invalid comment ids</li>
     *     <li>Disabled comments</li>
     * </ul>
     * @param problems the receptacle for any problems found
     * @param commentIdStream the stream of comment ids
     * @return the comments loaded
     */
    public List<Comment> validateCommentIds(Collection<String> problems, Stream<Integer> commentIdStream) {
        Set<Integer> commentIds = commentIdStream.collect(Collectors.toCollection(HashSet::new));
        if (commentIds.contains(null)) {
            problems.add("Null is not a valid comment ID.");
            commentIds.remove(null);
        }
        if (commentIds.isEmpty()) {
            return List.of();
        }
        List<Comment> comments = commentRepo.findAllByIdIn(commentIds);
        comments.forEach(com -> commentIds.remove(com.getId()));
        if (!commentIds.isEmpty()) {
            problems.add(String.format("Unknown comment %s: %s", commentIds.size()==1 ? "ID" : "IDs", commentIds));
        }
        List<String> disabledComments = comments.stream()
                .filter(c -> !c.isEnabled())
                .map(c -> String.format("(id=%s, category=%s, text=%s)", c.getId(), c.getCategory(), repr(c.getText())))
                .collect(toList());
        if (!disabledComments.isEmpty()) {
            problems.add(pluralise("Comment{s} not enabled: ", disabledComments.size())+disabledComments);
        }
        return comments;
    }
}
