package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.Comment;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Tests {@link CommentAdminService}
 * @author dr6
 */
public class TestCommentAdminService {
    private CommentRepo mockCommentRepo;
    private CommentAdminService service;

    @BeforeEach
    void setup() {
        mockCommentRepo = mock(CommentRepo.class);

        service = new CommentAdminService(mockCommentRepo,
                new StringValidator("category", 2, 16, StringValidator.CharacterType.ALPHA),
                new StringValidator("text", 2, 32, StringValidator.CharacterType.ALPHA));
    }

    private void setupCommentLists(List<Comment> comments) {
        when(mockCommentRepo.findAll()).thenReturn(comments);
        when(mockCommentRepo.findAllByCategory(any())).then(invocation -> {
            String category = invocation.getArgument(0);
            return comments.stream().filter(c -> c.getCategory().equalsIgnoreCase(category)).collect(toList());
        });
        when(mockCommentRepo.findAllByEnabled(anyBoolean())).then(invocation -> {
            boolean enabled = invocation.getArgument(0);
            return comments.stream().filter(c -> c.isEnabled()==enabled).collect(toList());
        });
        when(mockCommentRepo.findAllByCategoryAndEnabled(any(), anyBoolean())).then(invocation -> {
            String category = invocation.getArgument(0);
            boolean enabled = invocation.getArgument(1);
            return comments.stream().filter(c -> c.isEnabled()==enabled && c.getCategory().equalsIgnoreCase(category))
                    .collect(toList());
        });
    }

    @ParameterizedTest
    @MethodSource("getCommentsArgs")
    public void testGetComments(List<Comment> comments, String category, boolean includeDisabled,
                                Set<Comment> expectedComments) {
        setupCommentLists(comments);
        Iterable<Comment> results = service.getComments(category, includeDisabled);
        Set<Comment> commentSet = BasicUtils.stream(results).collect(toSet());
        assertEquals(expectedComments, commentSet);
    }

    static Stream<Arguments> getCommentsArgs() {
        Comment[] comments = {
                new Comment(1, "Alpha", "greek"),
                new Comment(2, "Beta", "greek", false),
                new Comment(3, "Alabama", "state"),
                new Comment(4, "Alaska", "state", false),
                new Comment(5, "Unicorn", "cryptozoology", false),
        };
        final List<Comment> commentList = Arrays.asList(comments);
        return Stream.of(
                Arguments.of(commentList, null, true, Set.of(comments)),
                Arguments.of(commentList, null, false, Set.of(comments[0], comments[2])),
                Arguments.of(commentList, "greek", true, Set.of(comments[0], comments[1])),
                Arguments.of(commentList, "greek", false, Set.of(comments[0])),
                Arguments.of(commentList, "cryptozoology", false, Set.of())
        );
    }

    @ParameterizedTest
    @MethodSource("addCommentArgs")
    public void testAddComment(String category, String text, Comment existingComment, Object expectedResult) {
        when(mockCommentRepo.findByCategoryAndText(any(), any())).thenReturn(Optional.ofNullable(existingComment));
        if (expectedResult instanceof Comment expectedComment) {
            when(mockCommentRepo.save(any())).thenReturn(expectedComment);
            assertEquals(expectedComment, service.addComment(category, text));
            verify(mockCommentRepo).save(new Comment(null, expectedComment.getText(), expectedComment.getCategory()));
            return;
        }
        Exception expectedException = (Exception) expectedResult;
        try {
            service.addComment(category, text);
            fail("No exception thrown");
        } catch (Exception ex) {
            assertSame(expectedException.getClass(), ex.getClass());
            assertEquals(expectedException.getMessage(), ex.getMessage());
        }
        verify(mockCommentRepo, never()).save(any());
    }

    static Stream<Arguments> addCommentArgs() {
        IllegalArgumentException noCategory = new IllegalArgumentException("Category not supplied.");
        IllegalArgumentException noText = new IllegalArgumentException("Text not supplied.");
        EntityExistsException exists = new EntityExistsException("Comment already exists: (category=\"cat\", text=\"text\")");

        final Comment comment = new Comment(1, "text", "cat");
        return Stream.of(
                Arguments.of(null, "text", null, noCategory),
                Arguments.of("", "text", null, noCategory),
                Arguments.of("   ", "text", null, noCategory),
                Arguments.of("cat", null, null, noText),
                Arguments.of("cat", "\n\t", null, noText),
                Arguments.of("cat", "text", comment, exists),
                Arguments.of("   cat\n", "\ttext   ", comment, exists),
                Arguments.of("cat", "text", null, comment),
                Arguments.of("!cat", "text", null, new IllegalArgumentException("category \"!cat\" contains invalid characters \"!\".")),
                Arguments.of("cat", "!text", null, new IllegalArgumentException("text \"!text\" contains invalid characters \"!\"."))
        );
    }

    @ParameterizedTest
    @MethodSource("setCommentEnabledArgs")
    public void testSetCommentEnabled(int id, boolean enabled, Comment comment) {
        if (comment==null) {
            EntityNotFoundException notFoundException = new EntityNotFoundException("Comment not found.");
            when(mockCommentRepo.getById(id)).thenThrow(notFoundException);
            assertSame(notFoundException,
                    assertThrows(notFoundException.getClass(), () -> service.setCommentEnabled(id, enabled)));
            verify(mockCommentRepo, never()).save(any());
            return;
        }
        boolean noop = (enabled==comment.isEnabled());
        when(mockCommentRepo.getById(id)).thenReturn(comment);
        when(mockCommentRepo.save(any())).then(Matchers.returnArgument());
        Comment result = service.setCommentEnabled(id, enabled);
        assertSame(comment, result);
        assertEquals(enabled, comment.isEnabled());
        verify(mockCommentRepo, times(noop ? 0 : 1)).save(any());
    }

    static Stream<Arguments> setCommentEnabledArgs() {
        return Stream.of(
                Arguments.of(3, true, null),
                Arguments.of(3, true, new Comment(3, "text", "cat", true)),
                Arguments.of(3, true, new Comment(3, "text", "cat", false)),
                Arguments.of(3, false, new Comment(3, "text", "cat", true)),
                Arguments.of(3, true, new Comment(3, "text", "cat", false))
        );
    }
}
