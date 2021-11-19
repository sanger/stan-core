package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Comment;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;
import static uk.ac.sanger.sccp.utils.BasicUtils.trimAndRequire;

/**
 * Service for dealing with {@link Comment comments}.
 * @author dr6
 */
@Service
public class CommentAdminService {
    private final CommentRepo commentRepo;
    private final Validator<String> commentCategoryValidator;
    private final Validator<String> commentTextValidator;

    @Autowired
    public CommentAdminService(CommentRepo commentRepo,
                               @Qualifier("commentCategoryValidator") Validator<String> commentCategoryValidator,
                               @Qualifier("commentTextValidator") Validator<String> commentTextValidator) {
        this.commentRepo = commentRepo;
        this.commentCategoryValidator = commentCategoryValidator;
        this.commentTextValidator = commentTextValidator;
    }

    /**
     * Gets comments, filtered with the given arguments.
     * @param category include only comments for the given category. If null, include comments for all categories
     * @param includeDisabled true to include disabled as well as enabled comments. False to only include enabled comments
     * @return all comments matching the given arguments
     */
    public Iterable<Comment> getComments(String category, boolean includeDisabled) {
        if (category==null) {
            return (includeDisabled ? commentRepo.findAll() : commentRepo.findAllByEnabled(true));
        }
        return (includeDisabled ? commentRepo.findAllByCategory(category) : commentRepo.findAllByCategoryAndEnabled(category, true));
    }

    /**
     * Creates a new comment
     * @param category the category for the comment
     * @param text the text of the comment
     * @return the created comment
     * @exception IllegalArgumentException if either parameter is blank or null
     * @exception EntityExistsException if a matching comment already exists
     */
    public Comment addComment(String category, String text) {
        category = trimAndRequire(category, "Category not supplied.");
        text = trimAndRequire(text, "Text not supplied.");
        commentCategoryValidator.checkArgument(category);
        commentTextValidator.checkArgument(text);
        var optComment = commentRepo.findByCategoryAndText(category, text);
        if (optComment.isPresent()) {
            throw new EntityExistsException(String.format("Comment already exists: (category=%s, text=%s)",
                    repr(category), repr(text)));
        }

        return commentRepo.save(new Comment(null, text, category));
    }

    /**
     * Enables or disabled an existing comment.
     * If the comment {@link Comment#isEnabled} already matches the {@code enabled} argument, then no change is saved
     * @param commentId the id of an existing comment
     * @param enabled whether the comment should be enabled
     * @return the updated comment
     * @exception EntityNotFoundException if no such comment exists
     */
    public Comment setCommentEnabled(int commentId, boolean enabled) {
        Comment comment = commentRepo.getById(commentId);
        if (comment.isEnabled() != enabled) {
            comment.setEnabled(enabled);
            comment = commentRepo.save(comment);
        }
        return comment;
    }
}
