package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AnalyserScanData;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/** Test {@link AnalyserScanDataServiceImp} */
class TestAnalyserScanDataService {
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private WorkRepo mockWorkRepo;
    @Mock
    private LabwareProbeRepo mockProbeRepo;
    @Mock
    private OperationRepo mockOpRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;

    @InjectMocks
    AnalyserScanDataServiceImp service;

    AutoCloseable mocking;

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
    void testLoadForBarcode() {
        Labware lw = EntityFactory.getTube();
        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        AnalyserScanData data = new AnalyserScanData();
        data.setBarcode(lw.getBarcode());
        doReturn(data).when(service).load(lw);

        assertSame(data, service.load(lw.getBarcode()));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadForLabware(boolean segmented) {
        Labware lw = EntityFactory.getTube();
        List<String> workNumbers = List.of("SGP1", "SGP2");
        List<String> probes = List.of("probe1", "probe2");
        doReturn(workNumbers).when(service).loadWorkNumbers(lw);
        doReturn(probes).when(service).loadProbes(lw);
        doReturn(segmented).when(service).loadCellSegmentationRecorded(lw);

        AnalyserScanData data = service.load(lw);
        assertEquals(lw.getBarcode(), data.getBarcode());
        assertSame(workNumbers, data.getWorkNumbers());
        assertSame(probes, data.getProbes());
        assertEquals(segmented, data.isCellSegmentationRecorded());
    }

    @Test
    void testLoadWorkNumbers_none() {
        Labware lw = EntityFactory.getTube();
        when(mockWorkRepo.findWorkIdsForLabwareId(lw.getId())).thenReturn(List.of());

        assertThat(service.loadWorkNumbers(lw)).isEmpty();
        verify(mockWorkRepo, never()).findAllById(any());
    }

    @Test
    void testLoadWorkNumbers() {
        Labware lw = EntityFactory.getTube();
        List<Work> works = Arrays.asList(EntityFactory.makeWorks("SGP1", "SGP2"));
        List<Integer> workIds = works.stream().map(Work::getId).toList();
        when(mockWorkRepo.findWorkIdsForLabwareId(lw.getId())).thenReturn(workIds);
        when(mockWorkRepo.findAllById(workIds)).thenReturn(works);

        assertThat(service.loadWorkNumbers(lw)).containsExactly("SGP1", "SGP2");
    }

    @Test
    void testLoadProbes_none() {
        Labware lw = EntityFactory.getTube();
        when(mockProbeRepo.findAllByLabwareIdIn(List.of(lw.getId()))).thenReturn(List.of());
        assertThat(service.loadProbes(lw)).isEmpty();
    }

    @Test
    void testLoadProbes() {
        Labware lw = EntityFactory.getTube();
        List<ProbePanel> probes = List.of(new ProbePanel(10, "Alpha"), new ProbePanel(11, "Beta"));
        List<LabwareProbe> lwProbes = List.of(
                new LabwareProbe(100, probes.get(0), 200, lw.getId(), "lot1", 1),
                new LabwareProbe(101, probes.get(1), 200, lw.getId(), "lot2", 2),
                new LabwareProbe(102, probes.get(0), 201, lw.getId(), "lot3", 3)
        );
        when(mockProbeRepo.findAllByLabwareIdIn(List.of(lw.getId()))).thenReturn(lwProbes);

        assertThat(service.loadProbes(lw)).containsExactlyInAnyOrder("Alpha", "Beta");
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testLoadCellSegmentationRecorded(boolean segmented) {
        Labware lw = EntityFactory.getTube();
        OperationType opType = EntityFactory.makeOperationType("Cell segmentation", null, OperationTypeFlag.IN_PLACE);
        List<Operation> ops;
        if (segmented) {
            ops = List.of(new Operation(100, opType, null, null, null));
        } else {
            ops = List.of();
        }
        when(mockOpTypeRepo.getByName("Cell segmentation")).thenReturn(opType);
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()))).thenReturn(ops);

        assertEquals(segmented, service.loadCellSegmentationRecorded(lw));
    }
}