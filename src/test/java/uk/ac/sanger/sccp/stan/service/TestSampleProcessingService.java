package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.AddExternalIdsRequest.AddressExternalName;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Tests {@link SampleProcessingServiceImp}
 * @author bt8, dr6
 **/
public class TestSampleProcessingService {
    @Mock
    private TissueRepo mockTissueRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private LabwareRepo mockLabwareRepo;
    @Mock
    private OperationService mockOpService;
    @Mock
    private Validator<String> mockExternalNameValidator;
    @InjectMocks
    private SampleProcessingServiceImp service;

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
    public void testValidAddExternalId() {
        User user = EntityFactory.getUser();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        tissue.setExternalName("");
        Sample sample = new Sample(1, "100", tissue, null);
        LabwareType lt = EntityFactory.makeLabwareType(1,1, "LT1");
        Labware lw = EntityFactory.makeLabware(lt, sample);
        OperationType opType = EntityFactory.makeOperationType("Add External ID", null);
        Operation op = new Operation(200, opType, null, null, user);

        when(mockLabwareRepo.getByBarcode(any())).thenReturn(lw);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);

        AddExternalIDRequest request = new AddExternalIDRequest(lw.getBarcode(), "ExternalName");
        OperationResult opRes = new OperationResult(List.of(op), List.of(lw));
        assertEquals(opRes, service.addExternalID(user, request));

        verify(mockLabwareRepo).getByBarcode(eq(lw.getBarcode()));
        verify(service).validateSamples(any(), eq(Set.of(sample)));
        verify(service).validateExternalName(any(), eq("ExternalName"));
        verify(mockTissueRepo).findAllByExternalName(eq("ExternalName"));
    }

    @Test
    public void testInvalidAddExternalId() {
        User user = EntityFactory.getUser();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        Sample sample = new Sample(1, "100", tissue, null);
        LabwareType lt = EntityFactory.makeLabwareType(1,1, "LT1");
        Labware lw = EntityFactory.makeLabware(lt, sample);

        when(mockLabwareRepo.getByBarcode(any())).thenReturn(lw);

        // Use an existing tissue with external name
        AddExternalIDRequest request = new AddExternalIDRequest(lw.getBarcode(), tissue.getExternalName());
        assertValidationException(() -> service.addExternalID(user, request), "The request could not be validated.",
                "The associated tissue already has an external identifier: " + tissue.getExternalName()
        );

        verify(mockLabwareRepo).getByBarcode(eq(lw.getBarcode()));
        verify(service).validateSamples(any(), eq(Set.of(sample)));
        verify(service).validateExternalName(any(), eq(tissue.getExternalName()));
        verify(mockTissueRepo).findAllByExternalName(eq(tissue.getExternalName()));
        verifyNoInteractions(mockOpTypeRepo);
    }

    @Test
    public void testExternalNameValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);

        when(mockTissueRepo.findAllByExternalName(any())).thenReturn(List.of(tissue));

        service.validateExternalName(problems, tissue.getExternalName());
        assertThat(problems).contains("External identifier is already associated with another sample: "+tissue.getExternalName());
        verify(mockTissueRepo).findAllByExternalName(eq(tissue.getExternalName()));
        verify(mockExternalNameValidator).validate(eq(tissue.getExternalName()), any());
    }

    @Test
    public void testNoSamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        service.validateSamples(problems, Set.of());
        assertThat(problems).contains("Could not find a sample associated with this labware");
    }

    @Test
    public void testTooManySamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        Sample sample1 = new Sample(1, "100", tissue, null);
        Sample sample2 = new Sample(2, "100", tissue, null);

        service.validateSamples(problems, Set.of(sample1, sample2));
        assertThat(problems).contains("There are too many samples associated with this labware");
    }

    @Test
    public void testExistingExternalIdSamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        Sample sample1 = new Sample(1, "100", tissue, null);

        service.validateSamples(problems, Set.of(sample1));
        assertThat(problems).contains("The associated tissue already has an external identifier: "+tissue.getExternalName());
    }

    @Test
    public void testNoReplicateSamplesValidation() {
        Collection<String> problems = new LinkedHashSet<>();
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tissue = EntityFactory.makeTissue(donor, sl);
        tissue.setReplicate("");
        tissue.setExternalName("");
        Sample sample1 = new Sample(1, "100", tissue, null);

        service.validateSamples(problems, Set.of(sample1));
        assertThat(problems).contains("The associated tissue does not have a replicate number");
    }

    @Test
    void testAddExternalIds_noLw() {
        AddExternalIdsRequest request = new AddExternalIdsRequest("STAN-1", List.of());
        User user = EntityFactory.getUser();
        mayAddProblem("Bad barcode", null).when(service).loadLabware(any(), any());
        assertValidationException(() -> service.addExternalIds(user, request), List.of("Bad barcode"));
        verify(service).loadLabware(any(), eq("STAN-1"));
        verifyNoInteractions(mockOpTypeRepo);
        verifyNoInteractions(mockOpService);
        verifyNoInteractions(mockTissueRepo);
    }

    @Test
    void testAddExternalIds_invalid() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        AddExternalIdsRequest request = new AddExternalIdsRequest(lw.getBarcode(), List.of(new AddressExternalName(new Address(1,1), "NAME1")));
        doReturn(lw).when(service).loadLabware(any(), any());
        mayAddProblem("Invalid thing", null).when(service).validate(any(), any(), any());
        assertValidationException(() -> service.addExternalIds(user, request), List.of("Invalid thing"));
        verify(service).loadLabware(any(), eq(lw.getBarcode()));
        verify(service).validate(any(), same(lw), same(request));
        verifyNoInteractions(mockOpTypeRepo);
        verifyNoInteractions(mockOpService);
        verifyNoInteractions(mockTissueRepo);
    }

    @Test
    void testAddExternalIds_valid() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        AddExternalIdsRequest request = new AddExternalIdsRequest(lw.getBarcode(), List.of(new AddressExternalName(new Address(1,1), "NAME1")));
        doReturn(lw).when(service).loadLabware(any(), any());
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tis1 = EntityFactory.makeTissue(donor, sl);
        Tissue tis2 = EntityFactory.makeTissue(donor, sl);
        tis1.setExternalName(null);
        tis2.setExternalName(null);
        Map<Tissue, String> tissueNames = Map.of(tis1, "NAME1", tis2, "NAME2");
        OperationType opType = EntityFactory.makeOperationType("Add External ID", null);
        Operation op = new Operation();
        op.setId(100);
        op.setOperationType(opType);
        doReturn(tissueNames).when(service).validate(any(), any(), any());
        when(mockOpTypeRepo.getByName(any())).thenReturn(opType);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(op);
        OperationResult result = service.addExternalIds(user, request);
        assertThat(result.getOperations()).containsExactly(op);
        assertThat(result.getLabware()).containsExactly(lw);
        assertEquals("NAME1", tis1.getExternalName());
        assertEquals("NAME2", tis2.getExternalName());
        verify(service).loadLabware(any(), eq(lw.getBarcode()));
        verify(service).validate(any(), same(lw), same(request));
        verify(mockOpTypeRepo).getByName("Add external ID");
        verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings={"", "STAN-404", "STAN-1"})
    void testLoadLabware(String barcode) {
        String expectedProblem = null;
        Labware lw = null;
        if (nullOrEmpty(barcode)) {
            expectedProblem = "No barcode supplied.";
        } else if (barcode.equals("STAN-404")) {
            expectedProblem = "No labware found with barcode \"STAN-404\".";
        } else {
            lw = EntityFactory.getTube();
        }
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        when(mockLabwareRepo.findByBarcode(any())).thenReturn(Optional.ofNullable(lw));
        assertSame(lw, service.loadLabware(problems, barcode));
        assertProblem(problems, expectedProblem);
        if (nullOrEmpty(barcode)) {
            verifyNoInteractions(mockLabwareRepo);
        } else {
            verify(mockLabwareRepo).findByBarcode(barcode);
        }
    }

    @Test
    void testValidate_noLw() {
        List<String> problems = new ArrayList<>();
        assertThat(service.validate(problems, null, new AddExternalIdsRequest())).isEmpty();
        assertThat(problems).isEmpty();
        verify(service, never()).makeAddressTissueMap(any(), any(), any());
        verify(service, never()).makeTissueNameMap(any(), any(), any());
        verify(service, never()).checkNames(any(), any());
    }

    @Test
    void testValidate_emptyLw() {
        List<String> problems = new ArrayList<>(1);
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        assertThat(service.validate(problems, lw, new AddExternalIdsRequest())).isEmpty();
        assertThat(problems).containsExactly("Labware "+lw.getBarcode()+" is empty.");
        verify(service, never()).makeAddressTissueMap(any(), any(), any());
        verify(service, never()).makeTissueNameMap(any(), any(), any());
        verify(service, never()).checkNames(any(), any());
    }

    @Test
    void testValidate_noNames() {
        List<String> problems = new ArrayList<>(1);
        Labware lw = EntityFactory.getTube();
        AddExternalIdsRequest request = new AddExternalIdsRequest(lw.getBarcode(), List.of());
        assertThat(service.validate(problems, lw, request)).isEmpty();
        assertThat(problems).containsExactly("No names specified.");
        verify(service, never()).makeAddressTissueMap(any(), any(), any());
        verify(service, never()).makeTissueNameMap(any(), any(), any());
        verify(service, never()).checkNames(any(), any());
    }

    @Test
    void testValidate_repeatedAddresses() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        List<String> problems = new ArrayList<>(2);
        Labware lw = EntityFactory.getTube();
        AddExternalIdsRequest request = new AddExternalIdsRequest(lw.getBarcode(),
                List.of(new AddressExternalName(A1, "Jeff"),
                        new AddressExternalName(A2, "Ford"),
                        new AddressExternalName(A1, "Jeffy"),
                        new AddressExternalName(A3, "Moop"),
                        new AddressExternalName(A3, "Moopy")));
        assertThat(service.validate(problems, lw, request)).isEmpty();
        assertThat(problems).containsExactly("Repeated slot address: A1", "Repeated slot address: A3");
        verify(service, never()).makeAddressTissueMap(any(), any(), any());
        verify(service, never()).makeTissueNameMap(any(), any(), any());
        verify(service, never()).checkNames(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    void testValidate_full(boolean hasProblems) {
        final Address A1 = new Address(1,1);
        List<String> problems = new ArrayList<>(hasProblems ? 3 : 0);
        Labware lw = EntityFactory.getTube();
        Tissue tis = lw.getFirstSlot().getSamples().getFirst().getTissue();
        AddExternalIdsRequest request = new AddExternalIdsRequest(lw.getBarcode(),
                List.of(new AddressExternalName(A1, "Jeff")));
        Map<Address, Tissue> addressTissues = Map.of(A1, tis);
        Map<Tissue, String> tissueNames = Map.of(tis, "Jeff");
        mayAddProblem(hasProblems ? "Problem 1" : null, addressTissues).when(service).makeAddressTissueMap(any(), any(), any());
        mayAddProblem(hasProblems ? "Problem 2" : null, tissueNames).when(service).makeTissueNameMap(any(), any(), any());
        mayAddProblem(hasProblems ? "Problem 3" : null).when(service).checkNames(any(), any());

        assertSame(tissueNames, service.validate(problems, lw, request));
        verify(service).makeAddressTissueMap(any(), same(lw), eq(Set.of(A1)));
        verify(service).makeTissueNameMap(any(), same(addressTissues), same(request.getAddressNames()));
        verify(service).checkNames(any(), same(tissueNames.values()));
        if (hasProblems) {
            assertThat(problems).containsExactly("Problem 1", "Problem 2", "Problem 3");
        } else {
            assertThat(problems).isEmpty();
        }
    }

    @Test
    void testMakeAddressTissueMap_problems() {
        Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3),
                A4 = new Address(1,4);
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setBarcode("STAN-1");
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tis1 = EntityFactory.makeTissue(donor, sl);
        Tissue tis2 = EntityFactory.makeTissue(donor, sl);
        Sample sam1a = new Sample(101, null, tis1, null);
        Sample sam1b = new Sample(102, null, tis1, null);
        Sample sam2 = new Sample(103, null, tis2, null);

        Slot slot1 = lw.getSlot(A1), slot2 = lw.getSlot(A2);
        slot1.addSample(sam1a);
        slot1.addSample(sam1b); // legal-- only one tissue
        slot2.addSample(sam1a);
        slot2.addSample(sam2); // bad -- two different tissues

        Set<Address> addresses = Set.of(A1, A2, A3, A4);
        List<String> problems = new ArrayList<>(3);
        var map = service.makeAddressTissueMap(problems, lw, addresses);
        assertSame(tis1, map.get(A1));
        assertThat(problems).containsExactlyInAnyOrder(
                "Invalid slot address for labware STAN-1: [A4]",
                "Slot is empty in labware STAN-1: [A3]",
                "Slot contains more than one tissue in labware STAN-1: [A2]"
        );
    }

    @Test
    void testMakeAddressTissueMap_ok() {
        Address A1 = new Address(1,1), A2 = new Address(1,2);
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setBarcode("STAN-1");
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue tis1 = EntityFactory.makeTissue(donor, sl);
        Tissue tis2 = EntityFactory.makeTissue(donor, sl);
        Sample sam1a = new Sample(101, null, tis1, null);
        Sample sam1b = new Sample(102, null, tis1, null);
        Sample sam2 = new Sample(103, null, tis2, null);

        Slot slot1 = lw.getSlot(A1), slot2 = lw.getSlot(A2);
        slot1.addSample(sam1a);
        slot1.addSample(sam1b); // legal-- only one tissue
        slot2.addSample(sam2); // bad -- two different tissues

        Set<Address> addresses = Set.of(A1, A2);
        List<String> problems = new ArrayList<>(0);
        var map = service.makeAddressTissueMap(problems, lw, addresses);
        assertSame(tis1, map.get(A1));
        assertSame(tis2, map.get(A2));
        assertThat(problems).isEmpty();
    }

    @Test
    void testMakeTissueNameMap_problems() {
        Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3),
                A4 = new Address(1,4);
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue[] tissues = IntStream.range(0, 3).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        tissues[0].setExternalName("EXT1");
        tissues[1].setExternalName(null);
        tissues[2].setExternalName(null);
        tissues[1].setReplicate(null);
        Map<Address, Tissue> addressTissues = Map.of(A1, tissues[0], A2, tissues[1], A3, tissues[2], A4, tissues[2]);
        List<AddressExternalName> adnames = List.of(
                new AddressExternalName(A1, "NAME1"),
                new AddressExternalName(A2, "NAME2"),
                new AddressExternalName(A3, "NAME3"),
                new AddressExternalName(A4, "NAME4")
        );
        List<String> problems = new ArrayList<>(3);
        service.makeTissueNameMap(problems, addressTissues, adnames);
        assertThat(problems).containsExactlyInAnyOrder(
                "Tissue already has an external name, in slot: [A1]",
                "Tissue does not have a replicate number, in slot: [A2]",
                "Different external names given for tissue: [[tissue in slot A3; A4]]"
        );
    }

    @Test
    void testMakeTissueNameMap_ok() {
        Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        Tissue[] tissues = IntStream.range(0, 2).mapToObj(i -> EntityFactory.makeTissue(donor, sl)).toArray(Tissue[]::new);
        tissues[0].setExternalName(null);
        tissues[1].setExternalName(null);
        Map<Address, Tissue> addressTissues = Map.of(A1, tissues[0], A2, tissues[1], A3, tissues[0]);
        List<AddressExternalName> adnames = List.of(
                new AddressExternalName(A1, "NAME1"),
                new AddressExternalName(A2, "NAME2"),
                new AddressExternalName(A3, "name1") // ok because same as NAME1 case insensitively
        );
        List<String> problems = new ArrayList<>(0);
        var map = service.makeTissueNameMap(problems, addressTissues, adnames);
        assertThat(problems).isEmpty();
        assertEquals("NAME1", map.get(tissues[0]));
        assertEquals("NAME2", map.get(tissues[1]));
    }

    @Test
    void testCheckNames_problems() {
        List<String> names = List.of("NAME1", "EXT1", "name1", "NAME*!");
        when(mockExternalNameValidator.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            if (name.indexOf('!') < 0) {
                return true;
            }
            Consumer<String> pcon = invocation.getArgument(1);
            pcon.accept("Invalid name: " + name);
            return false;
        });
        Tissue ext1 = EntityFactory.makeTissue(null, null);
        ext1.setExternalName("EXT1");
        when(mockTissueRepo.findAllByExternalNameIn(any())).thenReturn(List.of(ext1));
        List<String> problems = new ArrayList<>(3);
        service.checkNames(problems, names);
        assertThat(problems).containsExactlyInAnyOrder(
                "External name already in use: [EXT1]",
                "Invalid name: NAME*!",
                "Same external name given for different tissues: [\"name1\"]"
        );
        Set<String> distinctNames = Set.of("EXT1", "NAME1", "NAME*!");
        distinctNames.forEach(name -> verify(mockExternalNameValidator).validate(eq(name), any()));
        verify(mockTissueRepo).findAllByExternalNameIn(distinctNames);
    }

    @Test
    void testCheckNames_ok() {
        List<String> names = List.of("NAME1", "NAME2");
        when(mockTissueRepo.findAllByExternalNameIn(any())).thenReturn(List.of());
        List<String> problems = new ArrayList<>(0);
        when(mockExternalNameValidator.validate(any(), any())).thenReturn(true);
        service.checkNames(problems, names);
        assertThat(problems).isEmpty();
        verify(mockTissueRepo).findAllByExternalNameIn(Set.of("NAME1", "NAME2"));
        names.forEach(name -> verify(mockExternalNameValidator).validate(eq(name), any()));
    }
}
