package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import uk.ac.sanger.sccp.stan.model.Solution;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

@SpringBootTest
@ActiveProfiles(profiles = "test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSolutionRepo {
    @Autowired
    private SolutionRepo solutionRepo;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transactionStatus;

    private List<Solution> solutions;

    @BeforeAll
    void setup() {
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionStatus = transactionManager.getTransaction(transactionDefinition);
        solutions = asList(solutionRepo.saveAll(List.of(
                new Solution(101, "Solution 101"),
                new Solution(102, "Solution 102"),
                new Solution(103, "Solution 103", false)
        )));
    }

    @AfterAll
    void cleanup() {
        transactionManager.rollback(transactionStatus);
        solutions = null;
    }

    @Test
    void testFindByName() {
        for (Solution sol : solutions) {
            assertThat(solutionRepo.findByName(sol.getName())).contains(sol);
        }
        assertThat(solutionRepo.findByName("Bananas")).isEmpty();
    }

    @Test
    void testFindAllByEnabled() {
        assertThat(solutionRepo.findAllByEnabled(true)).containsExactlyInAnyOrderElementsOf(solutions.subList(0,2));
        assertThat(solutionRepo.findAllByEnabled(false)).containsExactly(solutions.get(2));
    }

    @Test
    void testFindAllByNameIn() {
        assertThat(solutionRepo.findAllByNameIn(List.of("Solution 101", "Solution 103", "Bananas")))
                .containsExactlyInAnyOrder(solutions.get(0), solutions.get(2));
    }

    @Test
    void testGetMapByIdIn() {
        final Map<Integer, Solution> map = solutionRepo.getMapByIdIn(List.of(solutions.get(0).getId(), solutions.get(2).getId()));
        assertThat(map).hasSize(2);
        IntStream.of(0,2).mapToObj(solutions::get)
                .forEach(sol -> assertEquals(sol, map.get(sol.getId())));

        assertThat(assertThrows(EntityNotFoundException.class, () -> solutionRepo.getMapByIdIn(List.of(solutions.get(1).getId(), -400))))
                .hasMessage("Unknown solution ID: [-400]");
    }

    @Test
    void testFindAllByIdIn() {
        assertThat(solutionRepo.findAllByIdIn(List.of(solutions.get(0).getId(), solutions.get(2).getId(), -400)))
                .containsExactly(solutions.get(0), solutions.get(2));
    }
}