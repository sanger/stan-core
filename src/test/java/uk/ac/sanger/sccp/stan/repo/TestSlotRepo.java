package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link SlotRepo}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles(profiles = "test")
@Import(EntityCreator.class)
public class TestSlotRepo {
    @Autowired
    SlotRepo slotRepo;

    @Autowired
    LabwareRepo labwareRepo;

    @Autowired
    SampleRepo sampleRepo;

    @Autowired
    EntityCreator entityCreator;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    public void testFindDistinctBySamplesIn() {
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1"),
                "TISSUE1");
        BioState bioState = entityCreator.anyBioState();
        List<Sample> samples = IntStream.range(0,3)
                .mapToObj(i -> entityCreator.createSample(tissue, i, bioState))
                .collect(toList());
        LabwareType lt = entityCreator.createLabwareType("lwtype", 1, 4);
        Labware lw = entityCreator.createLabware("STAN-A1", lt, samples.get(0), samples.get(0), samples.get(1));
        List<Slot> slots = lw.getSlots();
        slots.getFirst().getSamples().add(samples.get(1));
        // Slot 0 contains samples 0 and 1
        // Slot 1 contains sample 0
        // Slot 2 contains sample 1
        // Slot 3 is empty
        slots.set(0, slotRepo.save(slots.getFirst()));
        assertThat(slotRepo.findDistinctBySamplesIn(samples)).hasSize(3).hasSameElementsAs(slots.subList(0,3));
        assertThat(slotRepo.findDistinctBySamplesIn(samples.subList(1,3))).hasSize(2).containsOnly(slots.get(0), slots.get(2));
        assertThat(slotRepo.findDistinctBySamplesIn(samples.subList(2,3))).isEmpty();
    }

    @Test
    @Transactional
    public void testFindAllByIdIn() {
        Tissue tissue = entityCreator.createTissue(entityCreator.createDonor("DONOR1"),
                "TISSUE1");
        BioState bioState = entityCreator.anyBioState();
        List<Sample> samples = IntStream.range(0,3)
                .mapToObj(i -> entityCreator.createSample(tissue, i, bioState))
                .toList();
        LabwareType lt = entityCreator.createLabwareType("lwtype", 1, 4);
        Labware lw1 = entityCreator.createLabware("STAN-A1", lt, samples.get(0), samples.get(1));
        Labware lw2 = entityCreator.createLabware("STAN-A2", lt, samples.get(2));
        List<Slot> slots1 = lw1.getSlots();
        List<Slot> slots2 = lw2.getSlots();
        List<Integer> slot1Ids = slots1.stream().map(Slot::getId).collect(toList());
        List<Integer> slot2Ids = slots2.stream().map(Slot::getId).collect(toList());

        assertThat(slotRepo.findAllByIdIn(slot1Ids)).isEqualTo(slots1);
        assertThat(slotRepo.findAllByIdIn(slot2Ids)).isEqualTo(slots2);
    }

    @Test
    @Transactional
    public void testFindSlotIdsByLabwareIdIn() {
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 2);
        Labware lw1 = entityCreator.createLabware("STAN-1", lt);
        Labware lw2 = entityCreator.createLabware("STAN-2", lt);
        assertThat(slotRepo.findSlotIdsByLabwareIdIn(List.of(lw1.getId())))
                .containsExactlyInAnyOrder(lw1.getSlots().stream().map(Slot::getId).toArray(Integer[]::new));
        assertThat(slotRepo.findSlotIdsByLabwareIdIn(List.of(lw2.getId())))
                .containsExactlyInAnyOrder(lw2.getSlots().stream().map(Slot::getId).toArray(Integer[]::new));
        assertThat(slotRepo.findSlotIdsByLabwareIdIn(List.of(lw1.getId(), lw2.getId())))
                .containsExactlyInAnyOrder(Stream.of(lw1, lw2).flatMap(lw -> lw.getSlots().stream())
                        .map(Slot::getId).toArray(Integer[]::new));
    }
}
