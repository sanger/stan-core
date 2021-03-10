package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.PlanActionRepo;
import uk.ac.sanger.sccp.stan.repo.SlotRepo;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SampleService}
 * @author dr6
 */
public class TestSampleService {
    private PlanActionRepo mockPlanActionRepo;
    private SlotRepo mockSlotRepo;

    private SampleService sampleService;

    @BeforeEach
    void setup() {
        mockPlanActionRepo = mock(PlanActionRepo.class);
        mockSlotRepo = mock(SlotRepo.class);

        sampleService = new SampleService(mockPlanActionRepo, mockSlotRepo);
    }

    @ParameterizedTest
    @MethodSource("nextSectionData")
    public void testNextSection(Integer blockMaxSection, Integer planMaxSection,
                                int expectedNextSection) {
        Sample sample = EntityFactory.getSample();
        int slotId = 100;
        Slot slot = new Slot(slotId, 10, new Address(1,1),
                List.of(sample), sample.getId(), blockMaxSection);
        when(mockPlanActionRepo.findMaxPlannedSectionFromSlotId(slotId))
                .thenReturn(toOptional(planMaxSection));

        assertEquals(sampleService.nextSection(slot), expectedNextSection);
        verify(mockSlotRepo).save(slot);
        assertEquals(slot.getBlockSampleId(), sample.getId());
        assertEquals(slot.getBlockHighestSection(), expectedNextSection);
    }

    static Stream<Arguments> nextSectionData() {
        return Arrays.stream(new Integer[][] {
                {0, null, 1},
                {0, 0, 1},
                {4, null, 5},
                {4, 0, 5},
                {4, 2, 5},
                {0, null, 1},
                {0, 0, 1},
                {1, 2, 3},
                {0, 4, 5},
                {0, 4, 5},
                {2, 4, 5},
                {4, 4, 5},
        }).map(Arguments::of);
    }

    private static OptionalInt toOptional(Integer num) {
        return (num==null ? OptionalInt.empty() : OptionalInt.of(num));
    }
}
