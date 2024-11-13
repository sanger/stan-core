package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Test {@link BioRiskRepo} */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
class TestBioRiskRepo {
    @Autowired
    BioRiskRepo bioRiskRepo;
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    OperationRepo opRepo;

    @Test
    @Transactional
    public void testBioRisk() {
        assertThat(bioRiskRepo.findAll()).isEmpty();
        assertThat(bioRiskRepo.findAllByCodeIn(List.of("alpha", "beta"))).isEmpty();
        assertThat(bioRiskRepo.findByCode("alpha")).isEmpty();
        assertThat(bioRiskRepo.findAllByEnabled(true)).isEmpty();

        BioRisk alpha = bioRiskRepo.save(new BioRisk("alpha"));
        BioRisk beta = bioRiskRepo.save(new BioRisk("beta"));

        assertTrue(alpha.isEnabled());
        assertTrue(beta.isEnabled());

        assertThat(bioRiskRepo.findAll()).containsExactlyInAnyOrder(alpha, beta);
        assertThat(bioRiskRepo.findAllByEnabled(true)).containsExactlyInAnyOrder(alpha, beta);
        assertThat(bioRiskRepo.findByCode("alpha")).contains(alpha);
        assertThat(bioRiskRepo.findByCode("beta")).contains(beta);
        assertThat(bioRiskRepo.findAllByEnabled(false)).isEmpty();

        beta.setEnabled(false);
        bioRiskRepo.save(beta);
        assertThat(bioRiskRepo.findAllByEnabled(true)).containsExactly(alpha);
        assertThat(bioRiskRepo.findAllByEnabled(false)).containsExactly(beta);

        assertThat(bioRiskRepo.getByCode("alpha")).isEqualTo(alpha);
        assertThrows(EntityNotFoundException.class, () -> bioRiskRepo.getByCode("gamma"));
    }

    private int makeOpId() {
        OperationType opType = entityCreator.createOpType("opname", null);
        User user = entityCreator.createUser("username");
        Operation op = new Operation();
        op.setOperationType(opType);
        op.setUser(user);
        return opRepo.save(op).getId();
    }

    private Sample[] makeSamples(int num) {
        Tissue tissue = entityCreator.createTissue(null, null);
        return IntStream.range(0, num)
                .mapToObj(i -> entityCreator.createSample(tissue, null))
                .toArray(Sample[]::new);
    }

    @Test
    @Transactional
    void testSampleBioRisk() {
        Sample[] samples = makeSamples(2);
        int[] sampleIds = Arrays.stream(samples).mapToInt(Sample::getId).toArray();
        int opId = makeOpId();

        BioRisk risk1 = bioRiskRepo.save(new BioRisk("alpha"));
        BioRisk risk2 = bioRiskRepo.save(new BioRisk("beta"));
        assertThat(bioRiskRepo.loadBioRiskForSampleId(sampleIds[0])).isEmpty();

        bioRiskRepo.recordBioRisk(samples[0], risk1, opId);
        assertThat(bioRiskRepo.loadBioRiskForSampleId(sampleIds[0])).contains(risk1);
        assertThat(bioRiskRepo.loadBioRiskForSampleId(sampleIds[1])).isEmpty();

        bioRiskRepo.recordBioRisk(samples[1], risk2, opId);
        assertThat(bioRiskRepo.loadBioRiskForSampleId(sampleIds[0])).contains(risk1);
        assertThat(bioRiskRepo.loadBioRiskForSampleId(sampleIds[1])).contains(risk2);

        Map<Integer, Integer> brIdMap = bioRiskRepo.loadBioRiskIdsForSampleIds(List.of(sampleIds[0], sampleIds[1], -1));
        assertThat(brIdMap).hasSize(2);
        assertEquals(risk1.getId(), brIdMap.get(sampleIds[0]));
        assertEquals(risk2.getId(), brIdMap.get(sampleIds[1]));

        Map<Integer, BioRisk> brMap = bioRiskRepo.loadBioRisksForSampleIds(List.of(sampleIds[0], sampleIds[1], -1));
        assertThat(brMap).hasSize(2);
        assertEquals(risk1, brMap.get(sampleIds[0]));
        assertEquals(risk2, brMap.get(sampleIds[1]));
    }
}
