package uk.ac.sanger.sccp.stan.service.validation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertProblem;
import static uk.ac.sanger.sccp.stan.Matchers.mayAddProblem;

/**
 * Tests {@link ValidationHelperImp}
 */
class TestValidationHelper {
    @Mock
    private LabwareValidatorFactory mockLwValFactory;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private LabwareRepo mockLwRepo;
    @Mock
    private EquipmentRepo equipmentRepo;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private CommentValidationService mockCommentValidationService;

    @InjectMocks
    private ValidationHelperFactoryImp valFactory;

    @InjectMocks
    private ValidationHelperImp val;

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
    public void testProblems() {
        var problems = val.getProblems();
        assertThat(problems).isEmpty();
        val.addProblem("Problem 1.");
        assertThat(problems).containsExactly("Problem 1.");
    }

    @ParameterizedTest
    @CsvSource({
            "boo,,true,",
            "boo,,false,",
            "boo,true,true,",
            "boo,false,false,",
            ",,,Operation type not specified.",
            "boo,,,Unknown operation type: \"boo\"",
            "boo,true,false,Operation type boo cannot be used in this operation.",
            "boo,false,true,Operation type boo cannot be used in this operation.",
    })
    public void testCheckOpType(String opName, Boolean expectInPlace, Boolean actuallyInPlace, String expectedProblem) {
        OperationType opType;
        if (actuallyInPlace==null) {
            opType = null;
        } else if (actuallyInPlace) {
            opType = EntityFactory.makeOperationType(opName, null, OperationTypeFlag.IN_PLACE);
        } else {
            opType = EntityFactory.makeOperationType(opName, null);
        }
        Set<OperationTypeFlag> expectedFlags = Boolean.TRUE.equals(expectInPlace) ? EnumSet.of(OperationTypeFlag.IN_PLACE) : null;
        Set<OperationTypeFlag> expectedNotFlags = Boolean.FALSE.equals(expectInPlace) ? EnumSet.of(OperationTypeFlag.IN_PLACE) : null;
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.ofNullable(opType));

        assertSame(opType, val.checkOpType(opName, expectedFlags, expectedNotFlags, null));
        if (opType!=null) {
            verify(mockOpTypeRepo).findByName(opName);
        }
        assertProblem(val.getProblems(), expectedProblem);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckOpType_predicate(boolean passes) {
        String opName = "boo";
        OperationType opType = EntityFactory.makeOperationType(opName, null);
        when(mockOpTypeRepo.findByName(opName)).thenReturn(Optional.of(opType));
        //noinspection unchecked
        Predicate<OperationType> predicate = mock(Predicate.class);
        when(predicate.test(opType)).thenReturn(passes);
        assertSame(opType, val.checkOpType(opName, predicate));
        assertProblem(val.getProblems(), passes ? null : "Operation type boo cannot be used in this operation.");
    }

    @ParameterizedTest
    @CsvSource({
            "STAN-1,,",
            "STAN-1,Bad barcode,Bad barcode",
            "STAN-1 null,,Barcode missing.",
            "STAN-1 null STAN-2,,Barcode missing.",
            "null,,No barcode specified.",
            ",,No barcode specified.",
    })
    public void testCheckLabware(String barcodesJoined, String valProblem, String expectedProblem) {
        if (barcodesJoined==null) {
            assertThat(val.checkLabware(null)).isEmpty();
            assertThat(val.getProblems()).containsExactly("No barcodes specified.");
            return;
        }
        List<String> barcodes = split(barcodesJoined);
        List<String> nonEmptyBarcodes = barcodes.stream()
                .filter(s -> s!=null && !s.isEmpty())
                .collect(toList());
        if (nonEmptyBarcodes.isEmpty()) {
            assertThat(val.checkLabware(barcodes)).isEmpty();
            assertThat(val.getProblems()).containsExactly("Barcode missing.");
            return;
        }
        LabwareValidator lwVal = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(lwVal);
        when(lwVal.getErrors()).thenReturn(valProblem==null ? List.of() : List.of(valProblem));
        Labware lw = EntityFactory.getTube();
        when(lwVal.getLabware()).thenReturn(List.of(lw));

        UCMap<Labware> lwMap = val.checkLabware(barcodes);
        assertThat(lwMap).hasSize(1);
        assertSame(lw, lwMap.get(lw.getBarcode()));
        assertProblem(val.getProblems(), expectedProblem);

        verify(lwVal).loadLabware(mockLwRepo, nonEmptyBarcodes);
        verify(lwVal).validateSources();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckWork(boolean anyProblem) {
        String expectedProblem = (anyProblem ? "Bad work" : null);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, EntityFactory.makeWork("SGP1"));
        mayAddProblem(expectedProblem, workMap).when(mockWorkService).validateUsableWorks(any(), any());
        List<String> workNumbers = List.of("SGP1");

        assertSame(workMap, val.checkWork(workNumbers));

        verify(mockWorkService).validateUsableWorks(any(), same(workNumbers));
        assertProblem(val.getProblems(), expectedProblem);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckCommentIds(boolean anyProblem) {
        String expectedProblem = anyProblem ? "Bad comment id" : null;
        List<Comment> comments = List.of(new Comment(1, "A", "B"), new Comment(2, "C", "D"));
        mayAddProblem(expectedProblem, comments).when(mockCommentValidationService).validateCommentIds(any(), any());
        Stream<Integer> stream = IntStream.of(1,2,3).boxed();
        Map<Integer, Comment> commentMap = val.checkCommentIds(stream);
        assertThat(commentMap).hasSize(comments.size());
        comments.forEach(com -> assertSame(com, commentMap.get(com.getId())));
        assertProblem(val.getProblems(), expectedProblem);
        verify(mockCommentValidationService).validateCommentIds(any(), same(stream));
    }

    @ParameterizedTest
    @CsvSource({
            ",,,3,",
            "1,2,,4,",
            ",,3,4,",
            "3,,1,2,The specified timestamp is before labware STAN-1 was created.",
            "1,3,2,4,The specified time is before the preceding operation.",
            "1,2,4,3,The specified time is in the future.",
    })
    public void testCheckTimestamp(Integer lwDay, Integer priorOpDay, Integer specifiedDay, Integer nowDay,
                                   String expectedProblem) {
        Labware lw;
        if (lwDay==null) {
            lw = null;
        } else {
            lw = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
            lw.setBarcode("STAN-1");
            lw.setCreated(ldt(lwDay));
        }
        LocalDateTime priorOpTime = ldt(priorOpDay);
        LocalDateTime timestamp = ldt(specifiedDay);
        LocalDate today = LocalDate.of(2023,1,nowDay);

        if (priorOpTime==null) {
            val.checkTimestamp(timestamp, today, lw);
        } else {
            val.checkTimestamp(timestamp, today, lw==null ? null : List.of(lw), priorOpTime);
        }
        assertProblem(val.getProblems(), expectedProblem);
    }

    @Test
    public void testFactory() {
        assertThat(valFactory.getHelper()).isInstanceOf(ValidationHelperImp.class);
    }

    private static LocalDateTime ldt(Integer day) {
        if (day==null) {
            return null;
        }
        return LocalDateTime.of(2023,1,day, 12,0);
    }

    private static List<String> split(String joined) {
        if (joined==null) {
            return null;
        }
        if (joined.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(joined.split(" ",-1))
                .map(s -> s.equals("null") ? null : s)
                .collect(toList());
    }

    @ParameterizedTest
    @MethodSource("equipmentsAndValidations")
    public void testCheckEquipment_whenNoRequired(Integer requestEquipmentId, String requestCateogry, boolean required,
                                                  Equipment expectedEquipment, String expectedProblem) {
        when(equipmentRepo.findById(requestEquipmentId)).thenReturn(Optional.ofNullable(expectedEquipment));
        Equipment equipment  = val.checkEquipment(requestEquipmentId, requestCateogry, required);
        assertSame(expectedEquipment, equipment);
        assertProblem(val.getProblems(), expectedProblem);
    }

    static Stream<Arguments> equipmentsAndValidations() {
        final String category1 = "Alpha";
        final String category2 = "Beta";
        Equipment enabledEquipment = new Equipment(1, "Robot 1", category1, true);
        Equipment disabledEquipment = new Equipment(1, "Robot 1", category1, false);
        return Arrays.stream(new Object[][] {
                {1, category1, true, enabledEquipment, null},
                {1, category1, false, enabledEquipment, null},
                {1, null, false, enabledEquipment, null},
                {null, null, false, null, null},
                {null, category1, true, null, "No equipment id specified."},
                {1, category2, true, enabledEquipment, "Equipment Robot 1 (Alpha) cannot be used in this operation."},
                {2, null, false, null, "Unknown equipment id: 2"},
                {1, null, false, disabledEquipment, "Equipment Robot 1 is disabled."},
        }).map(Arguments::of);
    }

}