package uk.ac.sanger.sccp.stan.service;

import com.google.common.collect.Streams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityManager;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LabwareService}
 * @author dr6
 */
public class LabwareServiceTest {
    private LabwareRepo mockLabwareRepo;
    private SlotRepo mockSlotRepo;
    private BarcodeSeedRepo mockBarcodeSeedRepo;
    private EntityManager mockEntityManager;

    private LabwareService labwareService;
    private int idCounter = 1000;
    private List<Labware> savedLabware;
    private List<Slot> savedSlots;

    @BeforeEach
    void setup() {
        mockLabwareRepo = mock(LabwareRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockBarcodeSeedRepo = mock(BarcodeSeedRepo.class);
        mockEntityManager = mock(EntityManager.class);

        mockLabwareSave();
        mockSlotSave();
        mockRefresh();

        labwareService = spy(new LabwareService(mockEntityManager, mockLabwareRepo, mockSlotRepo, mockBarcodeSeedRepo));
        savedLabware = new ArrayList<>();
        savedSlots = new ArrayList<>();
    }

    void mockLabwareSave() {
        when(mockLabwareRepo.save(any())).then(invocation -> {
            Labware lw = invocation.getArgument(0);
            assertNull(lw.getId());
            lw.setId(++idCounter);
            savedLabware.add(lw);
            return lw;
        });
    }

    void mockSlotSave() {
        when(mockSlotRepo.save(any())).then(invocation -> {
            Slot slot = invocation.getArgument(0);
            assertNull(slot.getId());
            slot.setId(++idCounter);
            savedSlots.add(slot);
            return slot;
        });
    }

    void mockRefresh() {
        doAnswer(invocation -> {
            Labware lw = invocation.getArgument(0);
            final int lwId = lw.getId();
            lw.setSlots(savedSlots.stream().filter(slot -> slot.getLabwareId()==lwId).collect(toList()));
            return null;
        }).when(mockEntityManager).refresh(any(Labware.class));
    }

    @Test
    public void testCreateNoBarcode() {
        String barcode = "STAN-ABC";
        when(mockBarcodeSeedRepo.createStanBarcode()).thenReturn(barcode);
        Labware lw = EntityFactory.getTube();
        LabwareType lt = lw.getLabwareType();
        doReturn(lw).when(labwareService).create(any(Labware.class));
        Labware result = labwareService.create(lt);
        verify(labwareService).create(new Labware(null, null, lt, null));
        assertSame(lw, result);
    }

    @Test
    public void testCreateWithBarcode() {
        LabwareType lt = EntityFactory.makeLabwareType(2, 3);
        String barcode = "STAN-ABC";
        String externalBarcode = "EXT-11";
        Labware lw = labwareService.create(lt, barcode, externalBarcode);
        assertNotNull(lw.getId());
        assertEquals(barcode, lw.getBarcode());
        assertEquals(externalBarcode, lw.getExternalBarcode());
        assertEquals(lt, lw.getLabwareType());
        assertThat(savedLabware).hasSize(1).contains(lw);
        assertThat(savedSlots).hasSameElementsAs(lw.getSlots());
        //noinspection UnstableApiUsage
        Streams.forEachPair(Address.stream(lt.getNumRows(), lt.getNumColumns()), lw.getSlots().stream(),
                (address, slot) -> {
                    assertEquals(address, slot.getAddress());
                    assertEquals(slot.getLabwareId(), lw.getId());
                    assertNotNull(slot.getId());
                });
    }

    @Test
    public void testFindBySample() {
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId() + 1, 100, sample1.getTissue(), sample1.getBioState());

        LabwareType lt = EntityFactory.getTubeType();
        LabwareType lt2 = EntityFactory.makeLabwareType(1, 2);
        Labware[] labware = {
                EntityFactory.makeLabware(lt, sample1),
                EntityFactory.makeLabware(lt, sample2),
                EntityFactory.makeLabware(lt2, sample1, sample1),
                EntityFactory.makeLabware(lt2, sample1, sample2),
                EntityFactory.makeEmptyLabware(lt),
        };
        when(mockSlotRepo.findDistinctBySamplesIn(any())).then(invocation -> {
            Collection<Sample> samples = invocation.getArgument(0);
            return Arrays.stream(labware).flatMap(lw -> lw.getSlots().stream())
                    .filter(slot -> slot.getSamples().stream()
                            .anyMatch(samples::contains))
                    .collect(toList());
        });
        when(mockLabwareRepo.findAllByIdIn(any())).then(invocation -> {
            Collection<Integer> labwareIds = invocation.getArgument(0);
            return Arrays.stream(labware).filter(lw -> labwareIds.contains(lw.getId()))
                    .collect(toList());
        });

        assertThat(labwareService.findBySample(List.of(sample1)))
                .containsExactlyInAnyOrder(labware[0], labware[2], labware[3]);
        assertThat(labwareService.findBySample(List.of(sample2)))
                .containsExactlyInAnyOrder(labware[1], labware[3]);
        assertThat(labwareService.findBySample(List.of(sample1, sample2)))
                .containsExactlyInAnyOrder(labware[0], labware[1], labware[2], labware[3]);
        assertThat(labwareService.findBySample(List.of()))
                .isEmpty();
    }
}
