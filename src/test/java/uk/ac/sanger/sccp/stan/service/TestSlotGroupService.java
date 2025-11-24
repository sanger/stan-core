package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SlotGroupRepo;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.ac.sanger.sccp.stan.Matchers.returnArgument;

/** Test {@link SlotGroupServiceImp} */
class TestSlotGroupService {
    @Mock
    SlotGroupRepo mockSlotGroupRepo;
    @InjectMocks
    SlotGroupServiceImp service;

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
    void testSaveGroups() {
        Integer planId = 500;
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,3));
        List<List<Address>> groups = List.of(List.of(A1, A2), List.of(A3));
        when(mockSlotGroupRepo.saveAll(any())).then(returnArgument());
        List<SlotGroup> records = service.saveGroups(lw, planId, groups);
        verify(mockSlotGroupRepo).saveAll(records);
        assertThat(records).containsExactly(
                new SlotGroup(1, lw.getSlot(A1), planId),
                new SlotGroup(1, lw.getSlot(A2), planId),
                new SlotGroup(2, lw.getSlot(A3), planId)
        );
    }

    @Test
    void testLoadGroups() {
        Integer planId = 500;
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,3));
        List<SlotGroup> records = List.of(
                new SlotGroup(1, lw.getSlot(A1), planId),
                new SlotGroup(1, lw.getSlot(A2), planId),
                new SlotGroup(2, lw.getSlot(A3), planId)
        );
        Zip.enumerate(records.stream()).forEach((i, r) -> r.setId(i+1));
        when(mockSlotGroupRepo.findByPlanId(planId)).thenReturn(records);
        List<List<Address>> groups = service.loadGroups(planId);
        verify(mockSlotGroupRepo).findByPlanId(planId);
        assertThat(groups).containsExactly(List.of(A1, A2), List.of(A3));
    }
}