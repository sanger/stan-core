package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.Comment;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link CommentRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestCommentRepo {
    @Autowired
    CommentRepo commentRepo;

    @Test
    @Transactional
    public void testFindAllByIdIn() {
        Comment com1 = commentRepo.save(new Comment(null, "Comment1", "group1"));
        Comment com2 = commentRepo.save(new Comment(null, "Comment2", "group2"));
        commentRepo.save(new Comment(null, "Comment3", "group3"));
        assertThat(commentRepo.findAllByIdIn(List.of(com1.getId(), com2.getId(), -1))).containsOnly(com1, com2);
    }

    @Test
    @Transactional
    public void testFindAllByCategoryAndEnabled() {
        List<Comment> comments = Stream.of(
                new Comment(null, "Alpha", "AL"),
                new Comment(null, "Beta", "AL"),
                new Comment(null, "Gamma", "AL", false),
                new Comment(null, "Delta", "AK"),
                new Comment(null, "Epsilon", "AK", false)
        )
                .map(commentRepo::save)
                .collect(Collectors.toList());

        assertThat(commentRepo.findAllByCategoryAndEnabled("AL", true)).hasSameElementsAs(comments.subList(0,2));
    }
}
