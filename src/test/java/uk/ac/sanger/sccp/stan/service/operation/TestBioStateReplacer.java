package uk.ac.sanger.sccp.stan.service.operation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SampleRepo;
import uk.ac.sanger.sccp.stan.repo.SlotRepo;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * Tests {@link BioStateReplacer}
 * @author dr6
 */
public class TestBioStateReplacer {
    private SlotRepo mockSlotRepo;
    private SampleRepo mockSampleRepo;
    private BioStateReplacer bsr;

    @BeforeEach
    void setup() {
        mockSlotRepo = mock(SlotRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        bsr = spy(new BioStateReplacer(mockSlotRepo, mockSampleRepo));
    }

    @Test
    public void testUpdateBioStateInPlace() {
        BioState bs0 = new BioState(1, "Regular");
        BioState bs1 = new BioState(2, "Decaf");
        Tissue tissue = EntityFactory.getTissue();
        Sample s1 = new Sample(1, 1, tissue, bs0);
        Sample s2 = new Sample(2, 2, tissue, bs0);
        Sample s3 = new Sample(3, 3, tissue, bs1);
        Sample s4 = new Sample(4, 4, tissue, bs1);

        Sample s1b = new Sample(5, 1, tissue, bs1);
        Sample s2b = new Sample(6, 2, tissue, bs1);

        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        final Slot slotA1 = lw.getSlot(new Address(1,1));
        final Slot slotA2 = lw.getSlot(new Address(1,2));
        final Slot slotB1 = lw.getSlot(new Address(2,1));
        final Slot slotB2 = lw.getSlot(new Address(2,2));
        slotA1.setSamples(List.of(s1, s2));
        slotA2.setSamples(List.of(s2));
        slotB1.setSamples(List.of(s3, s4));
        slotB2.setSamples(List.of(s3));

        doReturn(s1b).when(bsr).replaceSample(same(bs1), same(s1), any());
        doReturn(s2b).when(bsr).replaceSample(same(bs1), same(s2), any());

        List<Action> expectedActions = List.of(
                new Action(null, null, slotA1, slotA1, s1b, s1),
                new Action(null, null, slotA1, slotA1, s2b, s2),
                new Action(null, null, slotA2, slotA2, s2b, s2),
                new Action(null, null, slotB1, slotB1, s3, s3),
                new Action(null, null, slotB1, slotB1, s4, s4),
                new Action(null, null, slotB2, slotB2, s3, s3)
        );
        assertEquals(expectedActions, bsr.updateBioStateInPlace(bs1, lw));

        assertThat(slotA1.getSamples()).containsExactly(s1b, s2b);
        assertThat(slotA2.getSamples()).containsExactly(s2b);
        assertThat(slotB1.getSamples()).containsExactly(s3, s4);
        assertThat(slotB2.getSamples()).containsExactly(s3);
        verify(mockSlotRepo, times(2)).save(any());
        verify(mockSlotRepo).save(slotA1);
        verify(mockSlotRepo).save(slotA2);
    }

    @Test
    public void testUpdateBioStateInPlace_null() {
        Labware lw = EntityFactory.getTube();
        assertNull(bsr.updateBioStateInPlace(null, lw));
        verify(bsr, never()).replaceSample(any(), any(), any());
    }

    @Test
    public void testReplaceSample() {
        BioState bs0 = new BioState(1, "Regular");
        BioState bs1 = new BioState(2, "Decaf");
        Tissue tissue = EntityFactory.getTissue();
        Sample sam1 = new Sample(1, 1, tissue, bs1);
        final Map<Integer, Sample> sampleMap = new HashMap<>();
        assertSame(sam1, bsr.replaceSample(bs1, sam1, sampleMap));
        assertThat(sampleMap).isEmpty();
        verifyNoInteractions(mockSampleRepo);

        Sample sam2 = new Sample(2, 2, tissue, bs0);
        Sample sam3 = new Sample(3, 2, tissue, bs1);
        when(mockSampleRepo.save(any())).thenReturn(sam3);
        assertSame(sam3, bsr.replaceSample(bs1, sam2, sampleMap));
        assertThat(sampleMap).hasSize(1);
        assertSame(sam3, sampleMap.get(sam2.getId()));
        assertSame(sam3, bsr.replaceSample(bs1, sam2, sampleMap));
        verify(mockSampleRepo).save(any());
        verify(mockSampleRepo).save(new Sample(null, 2, tissue, bs1));
    }

}
