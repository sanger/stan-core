package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.LabwareType;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LabwareTypeRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
public class TestLabwareTypeRepo {
    private final LabwareTypeRepo labwareTypeRepo;

    @Autowired
    public TestLabwareTypeRepo(LabwareTypeRepo labwareTypeRepo) {
        this.labwareTypeRepo = labwareTypeRepo;
    }

    @Test
    @Transactional
    public void testGetByName() {
        LabwareType lt = labwareTypeRepo.getByName("Proviasette");
        assertNotNull(lt);
        assertThat(lt.getName()).isEqualToIgnoringCase("Proviasette");
        assertThrows(EntityNotFoundException.class, () -> labwareTypeRepo.getByName("Bananas"));
    }

    @Test
    @Transactional
    public void testFindAllByNameIn() {
        List<LabwareType> lwTypes = labwareTypeRepo.findAllByNameIn(List.of("proviasette", "tube", "Bananas"));
        assertThat(lwTypes).hasSize(2);
        assertEquals(List.of("Proviasette", "Tube"), lwTypes.stream().map(LabwareType::getName).collect(toList()));
    }
}
