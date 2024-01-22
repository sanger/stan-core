package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link MeasurementServiceImp}
 */
class TestMeasurementService {
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private SlotRepo mockSlotRepo;
    @Mock
    private ActionRepo mockActionRepo;
    @Mock
    private MeasurementRepo mockMeasurementRepo;

    @InjectMocks
    private MeasurementServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setUp() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @MethodSource("getMeasurementFromLabwareOrParentArgs")
    void testGetMeasurementFromLabwareOrParent(Measurement expected,
                                               Labware lw,
                                               List<Measurement> directMeasurements,
                                               List<Integer> parentLabwareIds,
                                               List<Integer> parentLabwareSlotIds,
                                               List<Measurement> parentMeasurements) {
        final String name = "cq";
        final String barcode = lw.getBarcode();
        when(mockLwRepo.getByBarcode(any())).thenReturn(lw);
        List<Integer> slotIds = null;
        if (directMeasurements!=null) {
            slotIds = lw.getSlots().stream().map(Slot::getId).toList();
            when(mockMeasurementRepo.findAllBySlotIdInAndName(slotIds, name)).thenReturn(directMeasurements);
        }
        if (parentLabwareIds!=null) {
            when(mockActionRepo.findSourceLabwareIdsForDestinationLabwareIds(any())).thenReturn(parentLabwareIds);
        }
        if (parentLabwareSlotIds!=null) {
            when(mockSlotRepo.findSlotIdsByLabwareIdIn(any())).thenReturn(parentLabwareSlotIds);
        }
        if (parentMeasurements!=null) {
            when(mockMeasurementRepo.findAllBySlotIdInAndName(parentLabwareSlotIds, name)).thenReturn(parentMeasurements);
        }

        final Optional<Measurement> result = service.getMeasurementFromLabwareOrParent(barcode, name);
        if (expected==null) {
            assertThat(result).isEmpty();
        } else {
            assertThat(result).containsSame(expected);
        }

        verify(mockLwRepo).getByBarcode(barcode);
        verify(mockMeasurementRepo).findAllBySlotIdInAndName(slotIds, name);
        if (parentLabwareIds!=null) {
            verify(mockActionRepo).findSourceLabwareIdsForDestinationLabwareIds(List.of(lw.getId()));
        } else {
            verifyNoInteractions(mockActionRepo);
        }
        if (parentLabwareSlotIds!=null) {
            verify(mockSlotRepo).findSlotIdsByLabwareIdIn(parentLabwareIds);
        } else {
            verifyNoInteractions(mockSlotRepo);
        }
        if (parentMeasurements!=null) {
            verify(mockMeasurementRepo).findAllBySlotIdInAndName(parentLabwareSlotIds, name);
        }
    }

    static Stream<Arguments> getMeasurementFromLabwareOrParentArgs() {
        Labware lw = EntityFactory.getTube();
        List<Integer> parentLabwareIds = List.of(400,401);
        List<Integer> parentLabwareSlotIds = List.of(500,501);
        final Measurement meas = new Measurement(600, "alpha", "beta", 1, 2, 3);
        List<Measurement> measurements = List.of(meas);
        List<Measurement> noMeasurements = List.of();
        return Arrays.stream(new Object[][] {
                {null, lw, noMeasurements, List.of(), List.of(), noMeasurements},
                {meas, lw, measurements, null, null, null},
                {null, lw, noMeasurements, parentLabwareIds, parentLabwareSlotIds, noMeasurements},
                {meas, lw, noMeasurements, parentLabwareIds, parentLabwareSlotIds, measurements},
        }).map(Arguments::of);
    }
}