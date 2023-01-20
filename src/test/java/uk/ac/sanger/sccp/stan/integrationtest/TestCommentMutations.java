package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.Comment;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.transaction.Transactional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the comment admin mutations
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestCommentMutations {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private CommentRepo commentRepo;

    @Test
    @Transactional
    public void testAddCommentNonAdmin() throws Exception {
        String mutation = tester.readGraphQL("addnewcomment.graphql");
        tester.setUser(entityCreator.createUser("normo", User.Role.normal));
        Object result = tester.post(mutation);
        String errorMessage = chainGet(result, "errors", 0, "message");
        assertThat(errorMessage).contains("Requires role: admin");
        assertThat(commentRepo.findByCategoryAndText("section", "Fell in the bin.")).isEmpty();
    }

    @Test
    @Transactional
    public void testAddCommentAdmin() throws Exception {
        final String category = "section";
        final String text = "Fell in the bin.";
        String mutation = tester.readGraphQL("addnewcomment.graphql");
        tester.setUser(entityCreator.createUser("admo", User.Role.admin));
        Object result = tester.post(mutation);
        assertEquals(Map.of("category", category, "text", text, "enabled", true),
                chainGet(result, "data", "addComment"));
        Comment comment = commentRepo.findByCategoryAndText(category, text).orElseThrow();
        assertEquals(category, comment.getCategory());
        assertEquals(text, comment.getText());
        assertTrue(comment.isEnabled());
    }

    @Test
    @Transactional
    public void testSetCommentEnabled() throws Exception {
        Comment comment = BasicUtils.stream(commentRepo.findAll())
                .filter(Comment::isEnabled)
                .findAny()
                .orElseThrow();
        String mutation = tester.readGraphQL("setcommentenabled.graphql")
                .replace("666", String.valueOf(comment.getId()));
        tester.setUser(entityCreator.createUser("admo"));
        Object result = tester.post(mutation);
        assertEquals(Map.of("category", comment.getCategory(), "text", comment.getText(), "enabled", false),
                chainGet(result, "data", "setCommentEnabled"));

        assertFalse(commentRepo.getById(comment.getId()).isEnabled());
    }
}
