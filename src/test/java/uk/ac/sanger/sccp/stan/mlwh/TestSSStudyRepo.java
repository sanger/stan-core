package uk.ac.sanger.sccp.stan.mlwh;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link SSStudyRepoImp}
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
class TestSSStudyRepo {
    @Autowired
    private SSStudyRepo studyRepo;

    @Test
    public void testLoadAll() throws SQLException {
        List<SSStudy> studies = studyRepo.loadAllSs();
        assertThat(studies).containsExactlyInAnyOrder(
                new SSStudy(1, "Study Alpha"),
                new SSStudy(2, "Study Beta"),
                new SSStudy(3, "Study Gamma")
        );
    }
}