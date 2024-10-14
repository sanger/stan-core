package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.LabwareRoi;
import uk.ac.sanger.sccp.stan.request.LabwareRoi.RoiResult;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.service.operation.AnalyserServiceImp.RUN_NAME;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;

/** Test {@link RoiServiceImp} */
class TestRoiService {
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private RoiRepo mockRoiRepo;
    @Mock
    private SampleRepo mockSampleRepo;
    @Mock
    private LabwareNoteRepo mockLwNoteRepo;

    @InjectMocks
    private RoiServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    void testLabwareRois_none() {
        List<String> barcodes = List.of("STAN-1");
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(List.of());
        assertThat(service.labwareRois(barcodes)).isEmpty();
        verify(mockLwRepo).findByBarcodeIn(barcodes);
        verifyNoInteractions(mockRoiRepo);
    }

    @Test
    void testLabwareRois() {
        Sample[] samples = EntityFactory.makeSamples(2);
        int[] sampleIds = Arrays.stream(samples).mapToInt(Sample::getId).toArray();
        Map<Integer, Sample> sampleMap = Arrays.stream(samples)
                .collect(inMap(Sample::getId));
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware[] lws = {
                EntityFactory.makeLabware(lt, samples),
                EntityFactory.makeEmptyLabware(lt),
                EntityFactory.makeEmptyLabware(lt)
        };
        final List<Labware> lwList = Arrays.asList(lws);
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(lwList);
        for (int i = 1; i < lws.length; ++i) {
            Slot slot = lws[i].getFirstSlot();
            slot.addSample(samples[0]);
            slot.addSample(samples[1]);
        }
        int[] slotIds = Arrays.stream(lws)
                .flatMap(lw -> lw.getSlots().stream())
                .mapToInt(Slot::getId)
                .toArray();

        List<Roi> rois = List.of(
                new Roi(slotIds[0], sampleIds[0], 10, "Alpha"),
                new Roi(slotIds[1], sampleIds[1], 10, "Beta"),
                new Roi(slotIds[2], sampleIds[0], 11, "Gamma"),
                new Roi(slotIds[2], sampleIds[1], 11, "Delta")
        );
        when(mockRoiRepo.findAllBySlotIdIn(any())).thenReturn(rois);
        doReturn(sampleMap).when(service).loadSamples(any(), any());
        List<String> barcodes = List.of("STAN-1", "STAN-2");
        List<LabwareRoi> lwRois = service.labwareRois(barcodes);

        verify(mockLwRepo).findByBarcodeIn(barcodes);
        verify(service).loadSamples(lwList, rois);
        verify(mockRoiRepo).findAllBySlotIdIn(Arrays.stream(slotIds).boxed().collect(toSet()));

        assertThat(lwRois).hasSameSizeAs(lws);
        UCMap<LabwareRoi> lwRoiMap = UCMap.from(lwRois, LabwareRoi::getBarcode);
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        assertThat(lwRoiMap.get(lws[0].getBarcode()).getRois()).containsExactlyInAnyOrder(
                new RoiResult(slotIds[0], A1, samples[0], 10, "Alpha"),
                new RoiResult(slotIds[1], A2, samples[1], 10, "Beta")
        );
        assertThat(lwRoiMap.get(lws[1].getBarcode()).getRois()).containsExactlyInAnyOrder(
                new RoiResult(slotIds[2], A1, samples[0], 11, "Gamma"),
                new RoiResult(slotIds[2], A1, samples[1], 11, "Delta")
        );
        assertThat(lwRoiMap.get(lws[2].getBarcode()).getRois()).isEmpty();
    }

    @Test
    void testLabwareRunRois_unknownBarcode() {
        when(mockLwRepo.findByBarcode(any())).thenReturn(Optional.empty());
        assertThat(service.labwareRunRois("404", "run")).isEmpty();
        verify(mockLwRepo).findByBarcode("404");
        verifyNoInteractions(mockLwNoteRepo);
        verifyNoInteractions(mockRoiRepo);
    }

    @Test
    void testLabwareRunRois_noRuns() {
        Labware lw = EntityFactory.getTube();
        String bc = lw.getBarcode();
        when(mockLwRepo.findByBarcode(bc)).thenReturn(Optional.of(lw));
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(any(), any())).thenReturn(List.of());
        assertThat(service.labwareRunRois(bc, "run")).isEmpty();
        verify(mockLwRepo).findByBarcode(bc);
        verify(mockLwNoteRepo).findAllByLabwareIdInAndName(List.of(lw.getId()), RUN_NAME);
        verifyNoInteractions(mockRoiRepo);
    }

    @Test
    void testLabwareRunRois_noMatchingRun() {
        Labware lw = EntityFactory.getTube();
        String bc = lw.getBarcode();
        when(mockLwRepo.findByBarcode(bc)).thenReturn(Optional.of(lw));
        List<LabwareNote> runNotes = List.of(
                new LabwareNote(100, lw.getId(), 10, "run", "alpha"),
                new LabwareNote(101, lw.getId(), 11, "run", "beta")
        );
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(any(), any())).thenReturn(runNotes);
        assertThat(service.labwareRunRois(bc, "gamma")).isEmpty();
        verify(mockLwRepo).findByBarcode(bc);
        verify(mockLwNoteRepo).findAllByLabwareIdInAndName(List.of(lw.getId()), RUN_NAME);
        verifyNoInteractions(mockRoiRepo);
    }


    @Test
    void testLabwareRunRois_noRois() {
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,2));
        String bc = lw.getBarcode();
        when(mockLwRepo.findByBarcode(bc)).thenReturn(Optional.of(lw));
        List<LabwareNote> runNotes = List.of(
                new LabwareNote(100, lw.getId(), 10, "run", "alpha"),
                new LabwareNote(101, lw.getId(), 11, "run", "beta"),
                new LabwareNote(102, lw.getId(), 12, "run", "ALPHA")
        );
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(any(), any())).thenReturn(runNotes);
        when(mockRoiRepo.findAllByOperationIdIn(any())).thenReturn(List.of());
        assertThat(service.labwareRunRois(bc, "alpha")).isEmpty();
        verify(mockLwRepo).findByBarcode(bc);
        verify(mockLwNoteRepo).findAllByLabwareIdInAndName(List.of(lw.getId()), RUN_NAME);
        verify(mockRoiRepo).findAllByOperationIdIn(Set.of(10,12));
    }

    @Test
    void testLabwareRunRois_noMatchingRois() {
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,2));
        String bc = lw.getBarcode();
        when(mockLwRepo.findByBarcode(bc)).thenReturn(Optional.of(lw));
        List<LabwareNote> runNotes = List.of(
                new LabwareNote(100, lw.getId(), 10, "run", "alpha"),
                new LabwareNote(101, lw.getId(), 11, "run", "beta")
        );
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(any(), any())).thenReturn(runNotes);
        when(mockRoiRepo.findAllByOperationIdIn(any())).thenReturn(List.of(
                new Roi(-10, 20, 10, "roi1"),
                new Roi(-11, 21, 10, "roi2")
        ));
        assertThat(service.labwareRunRois(bc, "alpha")).isEmpty();
        verify(mockLwRepo).findByBarcode(bc);
        verify(mockLwNoteRepo).findAllByLabwareIdInAndName(List.of(lw.getId()), RUN_NAME);
        verify(mockRoiRepo).findAllByOperationIdIn(Set.of(10));
    }

    @Test
    void testLabwareRunRois_matchingRois() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,2));
        Integer slotId1 = lw.getFirstSlot().getId();
        Integer slotId2 = lw.getSlot(A2).getId();
        Sample[] sams = EntityFactory.makeSamples(2);
        Map<Integer, Sample> sampleMap = Arrays.stream(sams).collect(inMap(Sample::getId));
        String bc = lw.getBarcode();
        when(mockLwRepo.findByBarcode(bc)).thenReturn(Optional.of(lw));
        List<LabwareNote> runNotes = List.of(
                new LabwareNote(100, lw.getId(), 10, "run", "alpha"),
                new LabwareNote(101, lw.getId(), 11, "run", "beta")
        );
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(any(), any())).thenReturn(runNotes);
        final List<Roi> rois = List.of(
                new Roi(slotId1, sams[0].getId(), 10, "roi1"),
                new Roi(slotId2, sams[1].getId(), 10, "roi2")
        );
        when(mockRoiRepo.findAllByOperationIdIn(any())).thenReturn(rois);
        doReturn(sampleMap).when(service).loadSamples(any(), any());
        assertThat(service.labwareRunRois(bc, "alpha")).containsExactly(
                new RoiResult(slotId1, A1, sams[0], 10, "roi1"),
                new RoiResult(slotId2, A2, sams[1], 10, "roi2")
        );
        verify(mockLwRepo).findByBarcode(bc);
        verify(mockLwNoteRepo).findAllByLabwareIdInAndName(List.of(lw.getId()), RUN_NAME);
        verify(mockRoiRepo).findAllByOperationIdIn(Set.of(10));

        Map<Integer, Slot> slotMap = lw.getSlots().stream().collect(inMap(Slot::getId));

        verify(service).loadSamples(List.of(lw), rois);
        rois.forEach(roi -> verify(service).toRoiResult(roi, slotMap, sampleMap));
    }

    @Test
    public void testLoadSamples_noneMissing() {
        Sample[] samples = EntityFactory.makeSamples(2);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = {EntityFactory.makeEmptyLabware(lt), EntityFactory.makeEmptyLabware(lt)};
        labware[0].getFirstSlot().addSample(samples[0]);
        labware[1].getFirstSlot().addSample(samples[0]);
        labware[1].getFirstSlot().addSample(samples[1]);
        List<Roi> rois = List.of(
                new Roi(100, samples[0].getId(), 200, "roi1"),
                new Roi(101, samples[1].getId(), 201, "roi2")
        );
        Map<Integer, Sample> sampleMap = service.loadSamples(Arrays.asList(labware), rois);
        verifyNoInteractions(mockSampleRepo);
        assertThat(sampleMap).hasSize(samples.length);
        Arrays.stream(samples).forEach(sam -> assertSame(sampleMap.get(sam.getId()), sam));
    }

    @Test
    public void testLoadSamples_someMissing() {
        Sample[] samples = EntityFactory.makeSamples(4);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = {EntityFactory.makeEmptyLabware(lt), EntityFactory.makeEmptyLabware(lt)};
        labware[0].getFirstSlot().addSample(samples[0]);
        labware[0].getFirstSlot().addSample(samples[1]);
        labware[1].getFirstSlot().addSample(samples[1]);
        List<Roi> rois = List.of(
                new Roi(100, samples[0].getId(), 200, "roi1"),
                new Roi(101, samples[2].getId(), 201, "roi2"),
                new Roi(102, samples[2].getId(), 202, "roi3"),
                new Roi(103, samples[3].getId(), 203, "roi4")
        );
        when(mockSampleRepo.findAllByIdIn(any())).thenReturn(Arrays.asList(samples).subList(2,4));
        Map<Integer, Sample> sampleMap = service.loadSamples(Arrays.asList(labware), rois);
        verify(mockSampleRepo).findAllByIdIn(Set.of(samples[2].getId(), samples[3].getId()));
        assertThat(sampleMap).hasSize(samples.length);
        Arrays.stream(samples).forEach(sam -> assertSame(sampleMap.get(sam.getId()), sam));
    }

    @Test
    void testToRoiResult() {
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,2));
        Sample sample = EntityFactory.getSample();
        Map<Integer, Sample> sampleIdMap = Map.of(sample.getId(), sample);
        Map<Integer, Slot> slotIdMap = lw.getSlots().stream().collect(inMap(Slot::getId));
        final Address A2 = new Address(1,2);
        Slot slot = lw.getSlot(A2);
        Roi roi = new Roi(slot.getId(), sample.getId(), 2000, "Alpha");
        RoiResult rr = service.toRoiResult(roi, slotIdMap, sampleIdMap);
        assertEquals(new RoiResult(slot.getId(), A2, sample, 2000, "Alpha"), rr);
    }
}