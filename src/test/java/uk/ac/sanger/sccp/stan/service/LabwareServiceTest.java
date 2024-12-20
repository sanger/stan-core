package uk.ac.sanger.sccp.stan.service;

import com.google.common.collect.Streams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.LabwareService.SampleBioRisk;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
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
    private BarcodeIntRepo mockBarcodeIntRepo;
    private EntityManager mockEntityManager;
    private LabelTypeRepo mockLabelTypeRepo;
    private OperationRepo mockOperationRepo;
    private OperationTypeRepo mockOperationTypeRepo;
    private LabwareNoteRepo mockNoteRepo;
    private BioRiskRepo mockBioRiskRepo;
    private LabwareService labwareService;
    private int idCounter = 1000;
    private List<Labware> savedLabware;
    private List<Slot> savedSlots;

    @BeforeEach
    void setup() {
        mockLabwareRepo = mock(LabwareRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockBarcodeIntRepo = mock(BarcodeIntRepo.class);
        mockEntityManager = mock(EntityManager.class);
        mockLabelTypeRepo = mock(LabelTypeRepo.class);
        mockOperationRepo = mock(OperationRepo.class);
        mockOperationTypeRepo = mock(OperationTypeRepo.class);
        mockNoteRepo = mock(LabwareNoteRepo.class);
        mockBioRiskRepo = mock(BioRiskRepo.class);

        mockLabwareSave();
        mockSlotSave();
        mockRefresh();

        labwareService = spy(new LabwareService(mockEntityManager, mockLabwareRepo, mockSlotRepo, mockBarcodeIntRepo, mockLabelTypeRepo,
                                                mockOperationRepo, mockOperationTypeRepo, mockNoteRepo, mockBioRiskRepo));
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
        when(mockLabwareRepo.saveAll(any())).then(invocation -> {
            Iterable<Labware> lws = invocation.getArgument(0);
            for (Labware lw : lws) {
                assertNull(lw.getId());
                lw.setId(++idCounter);
                savedLabware.add(lw);
            }
            return lws;
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
        when(mockSlotRepo.saveAll(any())).then(invocation -> {
            Iterable<Slot> slots = invocation.getArgument(0);
            for (Slot slot : slots) {
                assertNull(slot.getId());
                slot.setId(++idCounter);
                savedSlots.add(slot);
            }
            return slots;
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
        when(mockBarcodeIntRepo.createStanBarcode()).thenReturn(barcode);
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
        assertThat(lw.getSlots()).hasSize(6);
        assertThat(savedSlots).hasSameSizeAs(lw.getSlots()).hasSameElementsAs(lw.getSlots());
        //noinspection UnstableApiUsage
        Streams.forEachPair(Address.stream(lt.getNumRows(), lt.getNumColumns()), lw.getSlots().stream(),
                (address, slot) -> {
                    assertEquals(address, slot.getAddress());
                    assertEquals(slot.getLabwareId(), lw.getId());
                    assertNotNull(slot.getId());
                });
    }

    @Test
    public void testCreateMultiple() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        List<String> barcodes = List.of("STAN-AA", "STAN-BB", "STAN-CC");
        when(mockBarcodeIntRepo.createStanBarcodes(3)).thenReturn(barcodes);
        List<Labware> lws = labwareService.create(lt, 3);
        assertThat(lws).hasSize(3);
        assertThat(savedLabware).hasSameSizeAs(lws).hasSameElementsAs(lws);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final List<Slot> allSlots = new ArrayList<>(6);
        //noinspection UnstableApiUsage
        Streams.forEachPair(lws.stream(), barcodes.stream(), (lw, bc) -> {
            assertEquals(lw.getBarcode(), bc);
            assertThat(lw.getSlots()).hasSize(2);
            assertEquals(lw.getFirstSlot().getAddress(), A1);
            assertEquals(lw.getSlots().get(1).getAddress(), A2);
            allSlots.addAll(lw.getSlots());
        });
        assertThat(savedSlots).hasSameSizeAs(allSlots).hasSameElementsAs(allSlots);
    }

    @Test
    public void testCreateZero() {
        assertThat(labwareService.create(null, 0)).isEmpty();
        assertThat(savedLabware).isEmpty();
    }

    @Test
    public void testCreateNegative() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> labwareService.create(null, -1)))
                .hasMessage("Cannot create a negative number of labware.");
        assertThat(savedLabware).isEmpty();
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

    @Test
    public void testCalculateLabelType() {
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId() + 1, 100, sample1.getTissue(), sample1.getBioState());
        Sample sample3 = new Sample(sample1.getId() + 2, 100, sample1.getTissue(), sample1.getBioState());
        Sample sample4 = new Sample(sample1.getId() + 3, 100, sample1.getTissue(), sample1.getBioState());

        LabelType slideLabel = new LabelType(51, "slide");
        LabelType fourSlotLabel = new LabelType(50, "4slotslide");
        LabwareType fourSlotSlide = new LabwareType(50, "4 slot slide", 4, 1, fourSlotLabel, false);
        Labware fourSampleLabware = EntityFactory.makeLabware(fourSlotSlide, sample1, sample2, sample3, sample4);
        Labware threeSampleLabware = EntityFactory.makeLabware(fourSlotSlide, sample1, sample2, sample3);
        Labware twoSampleLabware = EntityFactory.makeLabware(fourSlotSlide, sample1, sample2);
        Labware oneSampleLabware = EntityFactory.makeLabware(fourSlotSlide, sample1);

        when(mockLabelTypeRepo.getByName("Slide")).thenReturn(slideLabel);

        // When a 4 slot slide has 4 samples we should return the fourSlotLabel
        assertEquals(labwareService.calculateLabelType(fourSampleLabware), fourSlotLabel);
        // When we have less than 4 samples we should return slideLabel
        assertEquals(labwareService.calculateLabelType(threeSampleLabware), slideLabel);
        assertEquals(labwareService.calculateLabelType(twoSampleLabware), slideLabel);
        assertEquals(labwareService.calculateLabelType(oneSampleLabware), slideLabel);
    }

    @Test
    public void testGetLabwareOperations() {
        Sample sample1 = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeLabware(lt, sample1);

        OperationType stainOpType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.IN_PLACE);
        OperationType permOpType = EntityFactory.makeOperationType("Perm", null, OperationTypeFlag.IN_PLACE);
        Operation stainOp = EntityFactory.makeOpForLabware(stainOpType, List.of(lw1), List.of(lw1));

        doReturn(Optional.empty()).when(mockOperationTypeRepo).findByName(any());
        doReturn(Optional.of(stainOpType)).when(mockOperationTypeRepo).findByName("Stain");
        doReturn(Optional.of(permOpType)).when(mockOperationTypeRepo).findByName("Perm");

        doReturn(Optional.empty()).when(mockLabwareRepo).findByBarcode(any());
        doReturn(Optional.of(lw1)).when(mockLabwareRepo).findByBarcode(lw1.getBarcode());

        when(mockOperationRepo.findAllByOperationTypeAndDestinationLabwareIdIn(stainOpType, List.of(lw1.getId()))).thenReturn(List.of(stainOp));
        when(mockOperationRepo.findAllByOperationTypeAndDestinationLabwareIdIn(permOpType, List.of(lw1.getId()))).thenReturn(List.of());

        assertEquals(labwareService.getLabwareOperations(lw1.getBarcode(), "Stain"), List.of(stainOp));
        assertEquals(labwareService.getLabwareOperations(lw1.getBarcode(), "Perm"), List.of());
        Matchers.assertValidationException(() -> labwareService.getLabwareOperations("test", "Stain"), "The request could not be validated.", "Could not find labware with barcode \"test\".");
        Matchers.assertValidationException(() -> labwareService.getLabwareOperations(lw1.getBarcode(), "Space"), "The request could not be validated.", "\"Space\" operation type not found in database.");
    }

    @ParameterizedTest
    @MethodSource("labwareCostingArgs")
    public void testGetLabwareCosting(List<LabwareNote> notes, SlideCosting expected, Labware lw) {
        if (lw==null) {
            when(mockLabwareRepo.getByBarcode(any())).thenThrow(EntityNotFoundException.class);
            assertThrows(EntityNotFoundException.class, () -> labwareService.getLabwareCosting("STAN-404"));
            return;
        }
        when(mockLabwareRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        when(mockNoteRepo.findAllByLabwareIdInAndName(List.of(lw.getId()), "costing")).thenReturn(notes);
        assertSame(expected, labwareService.getLabwareCosting(lw.getBarcode()));
    }

    static Stream<Arguments> labwareCostingArgs() {
        Labware lw = EntityFactory.getTube();
        final String name = "costing";
        final String sgp = SlideCosting.SGP.name();
        final String faculty = SlideCosting.Faculty.name();
        LabwareNote note1sgp = new LabwareNote(1, lw.getId(), 11, name, sgp);
        LabwareNote note2fac = new LabwareNote(2, lw.getId(), 13, name, faculty);
        LabwareNote note3sgp = new LabwareNote(3, lw.getId(), 12, name, sgp);

        return Arrays.stream(new Object[][] {
                {null, null, null},
                {List.of(), null, lw},
                {List.of(note1sgp, note2fac, note3sgp), SlideCosting.SGP, lw},
                {List.of(note1sgp, note2fac), SlideCosting.Faculty, lw},
                {List.of(note2fac, note1sgp), SlideCosting.Faculty, lw},
                {List.of(note1sgp), SlideCosting.SGP, lw},
        }).map(Arguments::of);
    }

    @Test
    public void testGetSampleBioRiskCodes() {
        Sample[] samples = EntityFactory.makeSamples(3);
        Set<Integer> sampleIds = Arrays.stream(samples).map(Sample::getId).collect(toSet());
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Slot slot = lw.getFirstSlot();
        slot.addSample(samples[0]);
        slot.addSample(samples[1]);
        slot = lw.getSlots().get(2);
        slot.addSample(samples[1]);
        slot.addSample(samples[2]);
        when(mockLabwareRepo.getByBarcode(any())).thenReturn(lw);
        BioRisk[] risks = { new BioRisk(1, "risk1"), new BioRisk(2, "risk2")};
        Map<Integer, BioRisk> sampleIdRisks = Map.of(samples[0].getId(), risks[0], samples[1].getId(), risks[0], samples[2].getId(), risks[1]);
        when(mockBioRiskRepo.loadBioRisksForSampleIds(any())).thenReturn(sampleIdRisks);
        var results = labwareService.getSampleBioRisks(lw.getBarcode());
        assertThat(results).containsExactlyInAnyOrder(
                new SampleBioRisk(samples[0].getId(), "risk1"),
                new SampleBioRisk(samples[1].getId(), "risk1"),
                new SampleBioRisk(samples[2].getId(), "risk2")
        );
        verify(mockLabwareRepo).getByBarcode(lw.getBarcode());
        verify(mockBioRiskRepo).loadBioRisksForSampleIds(sampleIds);
    }
}
