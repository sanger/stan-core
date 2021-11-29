package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.Comment;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.EntityFactory.objToList;

/**
 * Tests {@link CommentValidationService}
 * @author dr6
 */
public class TestCommentValidationService {

    private CommentRepo mockCommentRepo;
    private CommentValidationService service;

    @BeforeEach
    void setup() {
        mockCommentRepo = mock(CommentRepo.class);
        service = new CommentValidationService(mockCommentRepo);
    }

    @ParameterizedTest
    @MethodSource("validateCommentIdsArgs")
    public void testValidateCommentIds(Object commentsObj, Object idsObj, Object expectedProblemsObj) {
        List<Comment> comments = objToList(commentsObj);
        List<Integer> ids = objToList(idsObj);
        List<String> expectedProblems = objToList(expectedProblemsObj);
        Set<Integer> expectedIds = ids.stream().filter(Objects::nonNull).collect(toSet());
        if (!expectedIds.isEmpty()) {
            when(mockCommentRepo.findAllByIdIn(expectedIds)).thenReturn(comments);
        }
        List<String> problems = new ArrayList<>(expectedProblems.size());

        assertSame(comments, service.validateCommentIds(problems, ids.stream()));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        verify(mockCommentRepo, times(expectedIds.isEmpty() ? 0 : 1)).findAllByIdIn(any());
    }

    static Stream<Arguments> validateCommentIdsArgs() {
        Comment enabledComment = new Comment(1, "Exploded", "section");
        Comment disabledComment = new Comment(2, "Imploded", "section", false);
        Comment anotherDisabledComment = new Comment(3, "Sideways", "section", false);
        List<Comment> comments = List.of(enabledComment, disabledComment, anotherDisabledComment);
        final String nullError = "Null is not a valid comment ID.";
        final String notEnabledError = "Comment not enabled: [(id=2, category=section, text=\"Imploded\")]";

        return Arrays.stream(new Object[][] {
                {null, null, null},
                {enabledComment, 1, null},
                {null, Collections.singletonList(null), nullError},
                {enabledComment, Arrays.asList(1, null), nullError},
                {null, 404, "Unknown comment ID: [404]"},
                {enabledComment, Arrays.asList(1, 404, 405), "Unknown comment IDs: [404, 405]"},
                {disabledComment, 2, notEnabledError},
                {comments, List.of(1,2,3),
                        "Comments not enabled: [(id=2, category=section, text=\"Imploded\"), (id=3, category=section, text=\"Sideways\")]"},
                {comments.subList(0,2), Arrays.asList(1,2,404,null,404,2,1), List.of(nullError, notEnabledError, "Unknown comment ID: [404]")},
        }).map(Arguments::of);
    }
}
