package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.LibConData;
import uk.ac.sanger.sccp.stan.service.LibraryConServiceImp.RequestData;
import uk.ac.sanger.sccp.stan.service.ReagentTransferValidatorService.LayoutTransfers;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.anyStream;
import static uk.ac.sanger.sccp.stan.Matchers.streamCaptor;

/** Test {@link LibraryConValidationServiceImp} */
class TestLibraryConValidationService {
    @Mock
    ValidationHelperFactory mockHelperFactory;
    @Mock
    ReagentTransferValidatorService mockRtValService;
    @Mock
    ReagentTransferService mockRtService;
    @Mock
    OpWithSlotMeasurementsService mockOwsmService;
    @Mock
    CommentValidationService mockComValService;

    @InjectMocks
    LibraryConValidationServiceImp service;

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
    void testValidate() {
        LibConData libConData = new LibConData(List.of(), EntityFactory.getUser(), List.of(new LibraryConRequest()));

        doNothing().when(service).initValidate(any());
        doNothing().when(service).rtValidate(any());
        doNothing().when(service).owsmValidate(any());

        service.validate(libConData);

        verify(service).initValidate(libConData);
        verify(service).rtValidate(libConData);
        verify(service).owsmValidate(libConData);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testInitValidate(boolean valid) {
        ValidationHelper helper = mock(ValidationHelper.class);
        when(mockHelperFactory.getHelper()).thenReturn(helper);
        LibraryConRequest request = new LibraryConRequest();
        User user = EntityFactory.getUser();
        Collection<String> problems = new HashSet<>();
        LibConData libConData = new LibConData(problems, user, List.of(request));
        List<RequestData> datas = libConData.data;
        doNothing().when(service).loadLabware(any(), any());
        doNothing().when(service).loadWork(any(), any());

        Set<String> helperProblems = (valid ? Set.of() : Set.of("Bad barcode.", "Bad work."));
        when(helper.getProblems()).thenReturn(helperProblems);

        service.initValidate(libConData);
        verify(service).loadLabware(helper, datas);
        verify(service).loadWork(helper, datas);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(helperProblems);
    }

    @Test
    void testRtValidate() {
        OperationType ot = EntityFactory.makeOperationType("Dual index plate", null);
        when(mockRtService.loadOpType(any(), anyString())).thenReturn(ot);
        ReagentPlate rp = new ReagentPlate("rp1", ReagentPlate.REAGENT_PLATE_TYPES.getFirst());
        UCMap<ReagentPlate> rpMap = UCMap.from(ReagentPlate::getBarcode, rp);
        LibraryConRequest request = new LibraryConRequest();
        request.setReagentPlateType(ReagentPlate.REAGENT_PLATE_TYPES.getLast());
        Address A1 = new Address(1,1);
        request.setReagentTransfers(List.of(new ReagentTransferRequest.ReagentTransfer("rp1", A1, A1)));
        Labware lw = EntityFactory.getTube();
        Set<String> problems = new HashSet<>();
        when(mockRtService.loadOpType(any(), anyString())).thenReturn(ot);
        when(mockRtService.loadReagentPlates(any())).thenReturn(rpMap);
        when(mockRtService.checkPlateType(any(), anyStream(), any())).thenReturn(ReagentPlate.REAGENT_PLATE_TYPES.getFirst());

        LibConData libConData = new LibConData(problems, null, List.of(request));
        RequestData data = libConData.data.getFirst();
        data.labware = lw;

        service.rtValidate(libConData);

        assertSame(ot, libConData.reagentOpType);
        assertSame(rpMap, libConData.reagentPlates);
        assertEquals(ReagentPlate.REAGENT_PLATE_TYPES.getFirst(), data.reagentPlateType);

        verify(mockRtService).loadOpType(same(problems), eq("Dual index plate"));
        ArgumentCaptor<Stream<ReagentPlate>> plateStreamCaptor = streamCaptor();
        verify(mockRtService).checkPlateType(same(problems), plateStreamCaptor.capture(), same(request.getReagentPlateType()));
        verify(mockRtValService).validateTransfers(same(problems), same(rpMap), eq(List.of(new LayoutTransfers(lw.layout(), request.getReagentTransfers()))));
        assertThat(plateStreamCaptor.getValue()).containsExactly(rp);
    }

    @Test
    void testRtValidate_nolw() {
        LibConData libConData = new LibConData(null, null, List.of());
        service.rtValidate(libConData);
        verify(mockRtValService, never()).validateTransfers(any(), any(), any());
    }

    @Test
    void testOwsmValidate() {
        Address A1 = new Address(1,1), A3 = new Address(1,3);
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Sample sample = EntityFactory.getSample();
        Set<Address> filledAddresses = Set.of(A1, A3);
        filledAddresses.forEach(ad -> lw.getSlot(ad).addSample(sample));
        OperationType ot = EntityFactory.makeOperationType("Amplification", null);
        when(mockOwsmService.loadOpType(any(), anyString())).thenReturn(ot);
        List<Comment> comments = List.of(new Comment(1, "com1", "cat"), new Comment(2, "com2", "cat"));
        doReturn(comments).when(service).loadComments(any());
        List<SlotMeasurementRequest> sanMeas = List.of(new SlotMeasurementRequest(A1, "name", "val", List.of(1,2)));
        when(mockOwsmService.sanitiseMeasurements(any(), any(), any())).thenReturn(sanMeas);
        LibraryConRequest request = new LibraryConRequest();
        request.setSlotMeasurements(List.of(new SlotMeasurementRequest(A1, "NAME", "VAL", List.of(1))));
        Set<String> problems = new HashSet<>();
        LibConData libConData = new LibConData(problems, null, List.of(request));
        RequestData data = libConData.data.getFirst();
        data.labware = lw;

        service.owsmValidate(libConData);
        assertSame(ot, libConData.ampOpType);
        assertSame(comments, libConData.comments);
        assertSame(sanMeas, data.sanitisedMeasurements);

        verify(mockOwsmService).validateAddresses(same(problems), eq(lw.layout()), eq(filledAddresses), same(request.getSlotMeasurements()));
        verify(mockOwsmService).loadOpType(same(problems), eq("Amplification"));
        verify(service).loadComments(libConData);
        verify(mockOwsmService).sanitiseMeasurements(same(problems), same(ot), same(request.getSlotMeasurements()));
        verify(mockOwsmService).checkForDupeMeasurements(same(problems), same(sanMeas));
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3})
    void testLoadLabware(int testcase) {
        boolean anyMissing = (testcase==1);
        boolean anyRepeated = (testcase==2);
        boolean anyInvalid = (testcase==3);
        List<String> barcodes = new ArrayList<>();
        barcodes.add("STAN-1");
        barcodes.add("STAN-2");
        if (anyMissing) {
            barcodes.add(null);
        }
        if (anyRepeated) {
            barcodes.add("stan-1");
        }
        if (anyInvalid) {
            barcodes.add("STAN-404");
        }
        List<LibraryConRequest> requests = barcodes.stream()
                .map(bc -> {
                    LibraryConRequest request = new LibraryConRequest();
                    request.setLabwareBarcode(bc);
                    return request;
                }).toList();
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw1.setBarcode("STAN-1");
        lw2.setBarcode("STAN-2");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        ValidationHelper helper = mock(ValidationHelper.class);
        when(helper.checkLabware(any())).thenReturn(lwMap);
        Set<String> problems = new HashSet<>();
        when(helper.getProblems()).thenReturn(problems);
        List<RequestData> datas = requests.stream().map(RequestData::new).toList();

        service.loadLabware(helper, datas);
        Set<String> expectedBarcodes = anyInvalid ? Set.of("STAN-1", "STAN-2", "STAN-404") : Set.of("STAN-1", "STAN-2");
        verify(helper).checkLabware(expectedBarcodes);
        List<Labware> expectedLabware = new ArrayList<>(requests.size());
        List<String> expectedProblems = new ArrayList<>();
        expectedLabware.add(lw1);
        expectedLabware.add(lw2);
        if (anyMissing) {
            expectedLabware.add(null);
            expectedProblems.add("Labware barcode missing from request.");
        }
        if (anyRepeated) {
            expectedLabware.add(lw1);
            expectedProblems.add("Labware barcode repeated: [\"STAN-1\"]");
        }
        if (anyInvalid) {
            expectedLabware.add(null);
        }
        Zip.of(datas.stream(), expectedLabware.stream()).forEach((d, lw) -> assertSame(lw, d.labware));
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @Test
    void testLoadWork() {
        Work work1 = EntityFactory.makeWork("SGP1");
        Work work2 = EntityFactory.makeWork("SGP2");
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work1, work2);
        ValidationHelper helper = mock(ValidationHelper.class);
        Set<String> problems = new HashSet<>();
        when(helper.getProblems()).thenReturn(problems);
        when(helper.checkWork(anyCollection())).thenReturn(workMap);

        String[] workNumbers = {"SGP1", "SGP2", "SGP1", "SGP404", null};
        List<LibraryConRequest> requests = Arrays.stream(workNumbers)
                .map(wn -> {
                    LibraryConRequest request = new LibraryConRequest();
                    request.setWorkNumber(wn);
                    return request;
                }).toList();
        List<RequestData> datas = requests.stream().map(RequestData::new).toList();

        service.loadWork(helper, datas);

        Set<String> expectedWorkNumbers = new HashSet<>(Arrays.asList("SGP1", "SGP2", "SGP404", null));
        verify(helper).checkWork(expectedWorkNumbers);
        Work[] expectedWorks = {work1, work2, work1, null, null};
        Zip.of(datas.stream(), Arrays.stream(expectedWorks)).forEach((d, w) -> assertSame(w, d.work));
    }

    @Test
    void testLoadComments() {
        SlotMeasurementRequest[] sms = Stream.of(List.of(1,2), List.of(3,4), List.of(4,5))
                .map(ids -> new SlotMeasurementRequest(null, null, null, ids))
                .toArray(SlotMeasurementRequest[]::new);
        List<LibraryConRequest> requests = List.of(new LibraryConRequest(), new LibraryConRequest());
        requests.getFirst().setSlotMeasurements(List.of(sms[0], sms[1]));
        requests.getLast().setSlotMeasurements(List.of(sms[2]));
        final List<String> problems = new ArrayList<>();
        LibConData libConData = new LibConData(problems, null, requests);
        List<Comment> comments = List.of(new Comment(1, "com1", "cat1"));
        when(mockComValService.validateCommentIds(any(), any())).thenReturn(comments);

        assertSame(comments, service.loadComments(libConData));

        ArgumentCaptor<Stream<Integer>> streamCaptor = streamCaptor();
        verify(mockComValService).validateCommentIds(same(problems), streamCaptor.capture());
        Stream<Integer> commentIdStream = streamCaptor.getValue();
        Set<Integer> commentIds = commentIdStream.collect(toSet());
        assertThat(commentIds).containsExactlyInAnyOrder(1,2,3,4,5);
    }
}
