package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.SpatialLocation;
import uk.ac.sanger.sccp.stan.model.TissueType;
import uk.ac.sanger.sccp.stan.repo.SpatialLocationRepo;
import uk.ac.sanger.sccp.stan.repo.TissueTypeRepo;
import uk.ac.sanger.sccp.stan.request.AddTissueTypeRequest;
import uk.ac.sanger.sccp.stan.request.AddTissueTypeRequest.NewSpatialLocation;

import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

class TestTissueTypeService {
    @Mock
    TissueTypeRepo mockTtRepo;
    @Mock
    SpatialLocationRepo mockSlRepo;
    @Mock
    Validator<String> mockTtNameValidator;
    @Mock
    Validator<String> mockTtCodeValidator;
    @Mock
    Validator<String> mockSlNameValidator;

    private TissueTypeServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new TissueTypeServiceImp(mockTtRepo, mockSlRepo,
                mockTtNameValidator, mockTtCodeValidator, mockSlNameValidator));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    void testPerform(boolean valid) {
        AddTissueTypeRequest request = new AddTissueTypeRequest("Bananas", "BNS");
        doNothing().when(service).sanitise(any());
        Set<String> problems = valid ? Set.of() : Set.of("Bad thing.");
        doReturn(problems).when(service).validate(any());
        if (valid) {
            TissueType newTissueType = EntityFactory.getTissueType();
            doReturn(newTissueType).when(service).execute(any());
            assertSame(newTissueType, service.perform(request));
        } else {
            assertValidationException(() -> service.perform(request), problems);
        }
        verify(service).sanitise(request);
        verify(service).validate(request);
        verify(service, times(valid ? 1 : 0)).execute(request);
    }

    @Test
    void testSanitise_null() {
        service.sanitise(null); // does nothing
    }

    @ParameterizedTest
    @CsvSource({
            ",,,",
            "Alpha,,Alpha,",
            ",ALP,,ALP",
            "'  Alpha  ','  alp',Alpha,ALP"
    })
    void testSanitise_tt(String iName, String iCode, String eName, String eCode) {
        AddTissueTypeRequest request = new AddTissueTypeRequest(iName, iCode);
        service.sanitise(request);
        assertEquals(eName, request.getName());
        assertEquals(eCode, request.getCode());
        assertThat(request.getSpatialLocations()).isEmpty();
    }

    @Test
    void testSanitise_sls() {
        List<NewSpatialLocation> sls = List.of(
                new NewSpatialLocation(0, null),
                new NewSpatialLocation(1, "Alpha"),
                new NewSpatialLocation(2, "  Beta ")
        );
        AddTissueTypeRequest request = new AddTissueTypeRequest(null, null, sls);
        service.sanitise(request);
        assertNull(request.getSpatialLocations().get(0).getName());
        assertEquals("Alpha", request.getSpatialLocations().get(1).getName());
        assertEquals("Beta", request.getSpatialLocations().get(2).getName());
    }

    @Test
    void testValidate_null() {
        Set<String> problems = service.validate(null);
        assertProblem(problems, "No request supplied.");
    }

    @Test
    void testValidate_problems() {
        when(mockTtNameValidator.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("Bad tt name: " + name);
            return false;
        });
        when(mockTtCodeValidator.validate(any(), any())).then(invocation -> {
            String code = invocation.getArgument(0);
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("Bad tt code: " + code);
            return false;
        });
        mayAddProblem("Bad SL").when(service).validateSpatialLocations(any(), any());
        mayAddProblem("Existing tt").when(service).checkExistingTissueTypes(any(), any(), any());

        AddTissueTypeRequest request = new AddTissueTypeRequest("Alpha", "ALP", List.of(new NewSpatialLocation(0, "sl0")));

        Collection<String> problems = service.validate(request);
        verify(mockTtNameValidator).validate(eq("Alpha"), any());
        verify(mockTtCodeValidator).validate(eq("ALP"), any());
        verify(service).validateSpatialLocations(any(), same(request.getSpatialLocations()));
        verify(service).checkExistingTissueTypes(any(), eq("Alpha"), eq("ALP"));

        assertThat(problems).containsExactlyInAnyOrder("Bad tt name: Alpha", "Bad tt code: ALP", "Bad SL", "Existing tt");
    }

    @Test
    void testValidate_noProblems() {
        when(mockTtNameValidator.validate(any(), any())).thenReturn(true);
        when(mockTtCodeValidator.validate(any(), any())).thenReturn(true);
        doNothing().when(service).validateSpatialLocations(any(), any());
        doNothing().when(service).checkExistingTissueTypes(any(), any(), any());
        AddTissueTypeRequest request = new AddTissueTypeRequest("Alpha", "ALP", List.of(new NewSpatialLocation(0, "sl0")));

        Collection<String> problems = service.validate(request);
        verify(mockTtNameValidator).validate(eq("Alpha"), any());
        verify(mockTtCodeValidator).validate(eq("ALP"), any());
        verify(service).validateSpatialLocations(any(), same(request.getSpatialLocations()));
        verify(service).checkExistingTissueTypes(any(), eq("Alpha"), eq("ALP"));
        assertThat(problems).isEmpty();
    }

    @Test
    public void testValidateSpatialLocations_null() {
        List<String> problems = new ArrayList<>(1);
        service.validateSpatialLocations(problems, null);
        assertProblem(problems, "No spatial locations specified.");
        verifyNoInteractions(mockSlNameValidator);
    }

    @Test
    public void testValidateSpatialLocations_valid() {
        List<NewSpatialLocation> sls = List.of(
                new NewSpatialLocation(0, "sl0"),
                new NewSpatialLocation(1, "sl1")
        );
        List<String> problems = new ArrayList<>(0);
        service.validateSpatialLocations(problems, sls);
        verify(mockSlNameValidator).validate(eq("sl0"), any());
        verify(mockSlNameValidator).validate(eq("sl1"), any());
        assertThat(problems).isEmpty();
    }

    @Test
    public void testValidateSpatialLocations_invalid() {
        List<NewSpatialLocation> sls = List.of(
                new NewSpatialLocation(0, "sl0"),
                new NewSpatialLocation(-5, "sl-5"),
                new NewSpatialLocation(1, "sl1"),
                new NewSpatialLocation(1, "sl2"),
                new NewSpatialLocation(3, "sl3!")
        );
        when(mockSlNameValidator.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            Consumer<String> consumer = invocation.getArgument(1);
            if (name.indexOf('!') < 0) {
                return true;
            }
            consumer.accept("Bad sl name: " + name);
            return false;
        });
        List<String> problems = new ArrayList<>(3);
        service.validateSpatialLocations(problems, sls);
        for (NewSpatialLocation sl : sls) {
            verify(mockSlNameValidator).validate(eq(sl.getName()), any());
        }
        assertThat(problems).containsExactlyInAnyOrder("Spatial location codes cannot be negative numbers.",
                "Spatial locations cannot contain duplicate codes.", "Bad sl name: sl3!");
    }

    @Test
    public void testCheckExistingTissueTypes_null() {
        List<String> problems = new ArrayList<>(0);
        service.checkExistingTissueTypes(problems, null, null);
        verifyNoInteractions(mockTtRepo);
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "code", "none"})
    public void testCheckExistingTissueTypes(String match) {
        boolean findMatch = !match.equals("none");
        List<String> problems = new ArrayList<>(findMatch ? 1 : 0);
        Optional<TissueType> found = findMatch ? Optional.of(new TissueType(10, "Alpha", "ALP")) : Optional.empty();
        when(mockTtRepo.findByName("alpha")).thenReturn(match.equals("name") ? found : Optional.empty());
        when(mockTtRepo.findByCode("alp")).thenReturn(match.equals("code") ? found : Optional.empty());

        service.checkExistingTissueTypes(problems, "alpha", "alp");
        String expectedProblem = switch(match) {
            case "name" -> "Tissue type already exists: Alpha";
            case "code" -> "Tissue type code already in use: ALP";
            default -> null;
        };
        assertProblem(problems, expectedProblem);
    }

    @Test
    public void testExecute() {
        AddTissueTypeRequest request = new AddTissueTypeRequest("Alpha", "ALP",
                List.of(new NewSpatialLocation(0, "sl0"),
                        new NewSpatialLocation(1, "sl1")));
        when(mockTtRepo.save(any())).then(invocation -> {
            TissueType tt = invocation.getArgument(0);
            tt.setId(10);
            return tt;
        });
        when(mockSlRepo.saveAll(any())).then(invocation -> {
            Iterable<SpatialLocation> sls = invocation.getArgument(0);
            for (SpatialLocation sl : sls) {
                sl.setId(100 + sl.getCode());
            }
            return sls;
        });
        TissueType expected = new TissueType(10, "Alpha", "ALP");
        expected.setSpatialLocations(List.of(new SpatialLocation(100, "sl0", 0, expected),
                new SpatialLocation(101, "sl1", 1, expected)));

        TissueType result = service.execute(request);
        assertEquals(expected, result);
        assertEquals(expected.getSpatialLocations(), result.getSpatialLocations());
        verify(mockTtRepo).save(result);
        verify(mockSlRepo).saveAll(result.getSpatialLocations());
    }
}