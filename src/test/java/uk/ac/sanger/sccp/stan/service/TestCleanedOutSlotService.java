package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.repo.OperationTypeRepo;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Test {@link CleanedOutSlotServiceImp} */
class TestCleanedOutSlotService {
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private OperationRepo mockOpRepo;

    @InjectMocks
    private CleanedOutSlotServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    void testFindCleanedOutSlots_noLw() {
        assertThat(service.findCleanedOutSlots(null)).isEmpty();
        assertThat(service.findCleanedOutSlots(List.of())).isEmpty();
        verifyNoInteractions(mockOpTypeRepo);
        verifyNoInteractions(mockOpRepo);
    }

    @Test
    void testFindCleanedOutSlots_noOpType() {
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.empty());
        assertThat(service.findCleanedOutSlots(List.of(EntityFactory.getTube()))).isEmpty();
        verify(mockOpTypeRepo).findByName("Clean out");
        verifyNoInteractions(mockOpRepo);
    }

    @Test
    void testFindCleanedOutSlots() {
        OperationType opType = EntityFactory.makeOperationType("Clean out", null, OperationTypeFlag.IN_PLACE);
        when(mockOpTypeRepo.findByName("Clean out")).thenReturn(Optional.of(opType));
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        List<Labware> labware = IntStream.range(0, 3).mapToObj(i -> EntityFactory.makeLabware(lt, sample, sample)).toList();
        final Address A1 = new Address(1,1), A2 = new Address(1,2);

        List<Slot> op1slots = List.of(labware.get(0).getSlot(A1));
        List<Slot> op2slots = List.of(labware.get(1).getSlot(A2));
        List<Operation> ops = List.of(
                EntityFactory.makeOpForSlots(opType, op1slots, op1slots, null),
                EntityFactory.makeOpForSlots(opType, op2slots, op2slots, null)
        );

        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);

        assertThat(service.findCleanedOutSlots(labware)).containsExactlyInAnyOrder(labware.get(0).getSlot(A1), labware.get(1).getSlot(A2));
        Set<Integer> lwIds = labware.stream().map(Labware::getId).collect(toSet());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(opType, lwIds);
    }
}