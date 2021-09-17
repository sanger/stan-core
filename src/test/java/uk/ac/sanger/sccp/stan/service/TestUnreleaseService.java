package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.UnreleaseRequest;
import uk.ac.sanger.sccp.stan.request.UnreleaseRequest.UnreleaseLabware;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link UnreleaseServiceImp}
 * @author dr6
 */
public class TestUnreleaseService {
    private LabwareValidatorFactory mockLabwareValidatorFactory;
    private LabwareRepo mockLwRepo;
    private SlotRepo mockSlotRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationService mockOpService;

    private UnreleaseServiceImp service;

    @BeforeEach
    void setup() {
        mockLabwareValidatorFactory = mock(LabwareValidatorFactory.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpService = mock(OperationService.class);

        service = spy(new UnreleaseServiceImp(mockLabwareValidatorFactory, mockLwRepo, mockSlotRepo,
                mockOpTypeRepo, mockOpService));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testUnrelease(boolean success) {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        UnreleaseRequest request = new UnreleaseRequest(List.of(new UnreleaseLabware(lw.getBarcode())));
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        OperationType opType = (success ? EntityFactory.makeOperationType("Unrelease", null, OperationTypeFlag.IN_PLACE) : null);
        when(mockOpTypeRepo.findByName("Unrelease")).thenReturn(Optional.ofNullable(opType));
        doReturn(lwMap).when(service).loadLabware(any(), any());
        doNothing().when(service).validateRequest(any(), any(), any());

        if (success) {
            OperationResult opRes = new OperationResult(List.of(), List.of(lw));
            doReturn(opRes).when(service).perform(any(), any(), any(), any());
            assertSame(opRes, service.unrelease(user, request));
        } else {
            ValidationException ex = assertThrows(ValidationException.class, () -> service.unrelease(user, request));
            assertThat(ex).hasMessage("The unrelease request could not be validated.");
            //noinspection unchecked
            assertThat((Collection<Object>) ex.getProblems()).containsExactly("Operation type \"Unrelease\" not found in database.");
        }

        verify(service).loadLabware(anyCollection(), same(request.getLabware()));
        verify(service).validateRequest(anyCollection(), same(lwMap), same(request.getLabware()));
        if (success) {
            verify(service).perform(user, request, opType, lwMap);
        } else {
            verify(service, never()).perform(any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @MethodSource("loadLabwareArgs")
    public void testLoadLabware(List<String> barcodes, List<Labware> labware, List<String> expectedProblems) {
        LabwareValidator val = spy(new LabwareValidator());
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(labware);
        when(mockLabwareValidatorFactory.getValidator()).thenReturn(val);
        if (expectedProblems==null) {
            expectedProblems = List.of();
        }

        List<String> problems = new ArrayList<>();
        List<UnreleaseLabware> requestLabware = barcodes.stream()
                .map(UnreleaseLabware::new)
                .collect(toList());

        UCMap<Labware> result = service.loadLabware(problems, requestLabware);

        assertEquals(UCMap.from(labware, Labware::getBarcode), result);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        List<String> nonNullBarcodes = barcodes.stream().filter(Objects::nonNull).collect(toList());
        if (!nonNullBarcodes.isEmpty()) {
            verify(val).loadLabware(mockLwRepo, nonNullBarcodes);
            verify(val).validateUnique();
            verify(val).validateNonEmpty();
            verify(val).validateState(any(), eq("not released"));
            verify(val).validateState(any(), eq("destroyed"));
            verify(val).validateState(any(), eq("discarded"));
            verify(val).getErrors();
        }
    }

    static Stream<Arguments> loadLabwareArgs() {
        Sample sam = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware activeLw = makeLw(lt, sam, "STAN-AC", null);
        Labware releasedLw = makeLw(lt, sam, "STAN-REL", lw -> lw.setReleased(true));
        Labware destroyedLw = makeLw(lt, sam, "STAN-DES", lw -> { lw.setReleased(true); lw.setDestroyed(true);});
        Labware discardedLw = makeLw(lt, sam, "STAN-DIS", lw -> { lw.setReleased(true); lw.setDiscarded(true);});
        Labware emptyLw = makeLw(lt, null, "STAN-EMP", null);
        return Arrays.stream(new Object[][] {
                {"STAN-REL", releasedLw, null},
                {"stan-rel", releasedLw, null},
                {null, null, "No labware specified."},
                {singletonList(null), null, "Null given as labware barcode."},
                {"STAN-AC", activeLw, "Labware is not released: [STAN-AC]."},
                {"STAN-DES", destroyedLw, "Labware is destroyed: [STAN-DES]."},
                {"STAN-DIS", discardedLw, "Labware is discarded: [STAN-DIS]."},
                {"STAN-EMP", emptyLw, List.of("Labware is empty: [STAN-EMP].", "Labware is not released: [STAN-EMP].")},
                {List.of("STAN-REL", "STAN-REL"), releasedLw, "Labware is repeated: [STAN-REL]."},
                {"STAN-404", null, "Invalid labware barcode: [\"STAN-404\"]."},
                {
                    Arrays.asList("STAN-REL", "stan-rel", null, "STAN-AC", "STAN-DES", "STAN-EMP", "STAN-404"),
                    List.of(releasedLw, releasedLw, activeLw, destroyedLw, emptyLw),
                    List.of("Null given as labware barcode.", "Labware is repeated: [STAN-REL].",
                            "Labware is empty: [STAN-EMP].", "Labware is not released: [STAN-AC, STAN-EMP].",
                            "Labware is destroyed: [STAN-DES].", "Invalid labware barcode: [\"STAN-404\"]."),
                },
        }).peek(arr -> {
            for (int i = 0; i < arr.length; ++i) {
                if (arr[i] ==null) {
                    arr[i] = List.of();
                } else if (!(arr[i] instanceof List)) {
                    arr[i] = List.of(arr[i]);
                }
            }
        }).map(Arguments::of);
    }

    static Labware makeLw(LabwareType lt, Sample sam, String barcode, Consumer<Labware> mod) {
        Labware lw = EntityFactory.makeLabware(lt, sam);
        lw.setBarcode(barcode);
        if (mod!=null) {
            mod.accept(lw);
        }
        return lw;
    }

    @Test
    public void testValidateRequest() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sam = EntityFactory.getSample();
        Labware[] labware = IntStream.range(0,3)
                .mapToObj(i -> {
                    Labware lw = EntityFactory.makeLabware(lt, sam);
                    lw.setBarcode("STAN-"+i);
                    return lw;
                }).toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, labware);
        doAnswer(invocation -> {
            Labware lw = invocation.getArgument(0);
            Integer n = invocation.getArgument(1);
            return "Problem from "+(lw==null ? null : lw.getBarcode())+": "+n;
        }).when(service).highestSectionProblem(any(), anyInt());
        final List<String> problems = new ArrayList<>();
        List<UnreleaseLabware> requestLabware = List.of(
                new UnreleaseLabware("STAN-0"),
                new UnreleaseLabware("STAN-1", 10),
                new UnreleaseLabware("STAN-2", 20),
                new UnreleaseLabware(null, 30),
                new UnreleaseLabware("STAN-404", 40)
        );

        service.validateRequest(problems, lwMap, requestLabware);

        verify(service, times(2)).highestSectionProblem(any(), anyInt());
        verify(service).highestSectionProblem(labware[1], 10);
        verify(service).highestSectionProblem(labware[2], 20);
        assertThat(problems).containsExactly("Problem from STAN-1: 10", "Problem from STAN-2: 20");
    }

    @ParameterizedTest
    @CsvSource(value={
            "true, 2, 5,",
            "true, 4, 3, 'For block STAN-1, cannot reduce the highest section number from 4 to 3.'",
            "true,,2,",
            "true,,-2,Cannot set the highest section to a negative number.",
            "false,,1,Cannot set the highest section number from labware STAN-1 because it is not a block.",
    })
    public void testHighestSectionProblem(boolean isBlock, Integer oldNum, int num, String expectedProblem) {
        Sample sample = EntityFactory.getSample();
        Labware lw = (isBlock ? EntityFactory.makeBlock(sample) : EntityFactory.makeLabware(EntityFactory.getTubeType(), sample));
        lw.setBarcode("STAN-1");
        if (oldNum!=null || isBlock) {
            lw.getFirstSlot().setBlockHighestSection(oldNum);
        }
        assertEquals(expectedProblem, service.highestSectionProblem(lw, num));
    }

    @Test
    public void testPerform() {
        Labware lw = EntityFactory.getTube();
        Operation op = new Operation();
        op.setId(40);
        User user = EntityFactory.getUser();
        OperationType opType = new OperationType();
        opType.setId(20);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UnreleaseRequest request = new UnreleaseRequest(List.of(new UnreleaseLabware(lw.getBarcode())));
        List<Labware> labwareList = List.of(lw);
        List<Operation> ops = List.of(op);
        doReturn(labwareList).when(service).updateLabware(any(), any());
        doReturn(ops).when(service).recordOps(any(), any(), any());

        OperationResult opRes = service.perform(user, request, opType, lwMap);
        assertEquals(new OperationResult(ops, labwareList), opRes);

        verify(service).updateLabware(request.getLabware(), lwMap);
        verify(service).recordOps(user, opType, labwareList);
    }

    @Test
    public void testUpdateLabware() {
        final List<Slot> slotUpdates = new ArrayList<>();
        final List<Labware> lwUpdates = new ArrayList<>();
        when(mockSlotRepo.saveAll(any())).then(invocation -> {
            List<Slot> slots = invocation.getArgument(0);
            slotUpdates.addAll(slots);
            return slots;
        });
        when(mockLwRepo.saveAll(any())).then(invocation -> {
            List<Labware> lw = invocation.getArgument(0);
            lwUpdates.addAll(lw);
            return lw;
        });

        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.range(0,5).mapToObj(i -> {
            Labware lw = EntityFactory.makeLabware(lt, sample);
            lw.setBarcode("STAN-" + i);
            lw.setReleased(true);
            if (i>0) {
                final Slot slot = lw.getFirstSlot();
                slot.setBlockSampleId(sample.getId());
                if (i < 4) {
                    slot.setBlockHighestSection(i);
                }
            }
            return lw;
        }).toArray(Labware[]::new);

        List<UnreleaseLabware> uls = List.of(
                new UnreleaseLabware("STAN-0"),
                new UnreleaseLabware("STAN-1", 5),
                new UnreleaseLabware("STAN-2", 10),
                new UnreleaseLabware("STAN-3", 3),
                new UnreleaseLabware("STAN-4", 12)
        );
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, labware);

        List<Labware> result = service.updateLabware(uls, lwMap);

        assertThat(result).containsExactly(labware);
        assertThat(lwUpdates).containsExactly(labware);

        for (Labware lw : lwUpdates) {
            assertFalse(lw.isReleased());
        }
        Integer[] expectedHighestSection = { null, 5, 10, 3, 12 };
        for (int i = 0; i < labware.length; ++i) {
            assertEquals(expectedHighestSection[i], labware[i].getFirstSlot().getBlockHighestSection());
        }
        Slot[] expectedSlotUpdates = IntStream.of(1,2,4).mapToObj(i -> labware[i].getFirstSlot()).toArray(Slot[]::new);
        assertThat(slotUpdates).containsExactly(expectedSlotUpdates);
    }

    @Test
    public void testRecordOps() {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = List.of(EntityFactory.makeLabware(lt, sample), EntityFactory.makeLabware(lt, sample));
        OperationType opType = EntityFactory.makeOperationType("Unrelease", null, OperationTypeFlag.IN_PLACE);
        User user = EntityFactory.getUser();

        Operation op1 = new Operation(1, opType, null, null, user);
        Operation op2 = new Operation(2, opType, null, null, user);
        when(mockOpService.createOperationInPlace(opType, user, labware.get(0), null, null)).thenReturn(op1);
        when(mockOpService.createOperationInPlace(opType, user, labware.get(1), null, null)).thenReturn(op2);

        assertThat(service.recordOps(user, opType, labware)).containsExactly(op1, op2);
        verify(mockOpService, times(labware.size())).createOperationInPlace(any(), any(), any(), any(), any());
    }
}
