package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SampleService}
 * @author dr6
 */
public class TestSampleService {
    private SampleRepo mockSampleRepo;
    private PlanActionRepo mockPlanActionRepo;
    private SlotRepo mockSlotRepo;

    private SampleService sampleService;

    @BeforeEach
    void setup() {
        mockSampleRepo = mock(SampleRepo.class);
        mockPlanActionRepo = mock(PlanActionRepo.class);
        mockSlotRepo = mock(SlotRepo.class);

        sampleService = new SampleService(mockSampleRepo, mockPlanActionRepo, mockSlotRepo);
    }

    @ParameterizedTest
    @MethodSource("nextSectionData")
    public void testNextSection(Integer blockMaxSection, Integer sampleMaxSection, Integer planMaxSection,
                                int expectedNextSection) {
        Sample sample = EntityFactory.getSample();
        final int tissueId = sample.getTissue().getId();
        Slot slot = new Slot(100, 10, new Address(1,1),
                List.of(sample), sample.getId(), blockMaxSection);
        when(mockSampleRepo.findMaxSectionForTissueId(tissueId))
                .thenReturn(toOptional(sampleMaxSection));
        when(mockPlanActionRepo.findMaxPlannedSectionForTissueId(tissueId))
                .thenReturn(toOptional(planMaxSection));

        assertEquals(sampleService.nextSection(slot), expectedNextSection);
        verify(mockSlotRepo).save(slot);
        assertEquals(slot.getBlockSampleId(), sample.getId());
        assertEquals(slot.getBlockHighestSection(), expectedNextSection);
    }

    static Stream<Arguments> nextSectionData() {
        return Arrays.stream(new Integer[][] {
                {0, null, null, 1},
                {0, 0, 0, 1},
                {4, null, null, 5},
                {4, 0, 0, 5},
                {4, 1, 2, 5},
                {0, 4, null, 5},
                {0, 4, 0, 5},
                {1, 4, 2, 5},
                {0, null, 4, 5},
                {0, 0, 4, 5},
                {2, 3, 4, 5},
                {4, 4, 4, 5},
        }).map(Arguments::of);
    }

    private static OptionalInt toOptional(Integer num) {
        return (num==null ? OptionalInt.empty() : OptionalInt.of(num));
    }
}
