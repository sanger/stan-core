package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.PlanData;
import uk.ac.sanger.sccp.stan.request.plan.*;
import uk.ac.sanger.sccp.stan.service.LabwareService;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PlanServiceImp}
 * @author dr6
 */
public class TestPlanService {
    private PlanServiceImp planService;

    private PlanValidationFactory mockPlanValidationFactory;
    private PlanValidation mockPlanValidation;
    private LabwareService mockLwService;

    private PlanOperationRepo mockPlanRepo;
    private PlanActionRepo mockPlanActionRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLwRepo;
    private LabwareTypeRepo mockLtRepo;
    private LabwareNoteRepo mockLwNoteRepo;
    private BioStateRepo mockBsRepo;

    private User user;

    @BeforeEach
    void setup() {
        mockPlanValidationFactory = mock(PlanValidationFactory.class);
        mockPlanValidation = mock(PlanValidation.class);
        mockLwService = mock(LabwareService.class);
        mockPlanRepo = mock(PlanOperationRepo.class);
        mockPlanActionRepo = mock(PlanActionRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockLtRepo = mock(LabwareTypeRepo.class);
        mockLwNoteRepo = mock(LabwareNoteRepo.class);
        mockBsRepo = mock(BioStateRepo.class);

        user = EntityFactory.getUser();

        when(mockPlanValidationFactory.createPlanValidation(any())).thenReturn(mockPlanValidation);

        planService = spy(new PlanServiceImp(mockPlanValidationFactory, mockLwService, mockPlanRepo,
                mockPlanActionRepo, mockOpTypeRepo, mockLwRepo, mockLtRepo, mockLwNoteRepo, mockBsRepo));
    }

    @Test
    public void testRecordInvalidPlan() {
        final List<String> problems = List.of("Problem A", "Problem B");
        when(mockPlanValidation.validate()).thenReturn(problems);

        doReturn(null).when(planService).executePlanRequest(any(), any());

        PlanRequest request = new PlanRequest("Bananas", List.of());

        try {
            planService.recordPlan(user, request);
            fail("Expected ValidationException");
        } catch (ValidationException ve) {
            assertEquals(ve.getMessage(), "The plan request could not be validated.");
            // noinspection unchecked,rawtypes
            assertThat(ve.getProblems()).hasSameElementsAs((Iterable) problems);
        }
        verify(mockPlanValidationFactory).createPlanValidation(request);
        verify(mockPlanValidation).validate();
        verify(planService, never()).executePlanRequest(any(), any());
    }

    @Test
    public void testRecordValidPlan() {
        when(mockPlanValidation.validate()).thenReturn(List.of());
        PlanResult result = new PlanResult();
        doReturn(result).when(planService).executePlanRequest(any(), any());

        PlanRequest request = new PlanRequest("Bananas", List.of());

        planService.recordPlan(user, request);

        verify(mockPlanValidationFactory).createPlanValidation(request);
        verify(mockPlanValidation).validate();
        verify(planService).executePlanRequest(user, request);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testExecutePlanRequest_noFetalWaste(boolean useOpBs) {
        PlanOperation[] plans = IntStream.range(10, 12)
                .mapToObj(id -> {
                    PlanOperation plan = new PlanOperation();
                    plan.setId(id);
                    return plan;
                }).toArray(PlanOperation[]::new);
        doReturn(plans[0], plans[1]).when(planService).createPlan(any(), any());

        UCMap<Labware> sources = UCMap.from(Labware::getBarcode,
                new Labware(1, "STAN-1A", EntityFactory.getTubeType(), null));
        doReturn(sources).when(planService).lookUpSources(any());

        final Labware tube1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        final Labware tube2 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        List<Labware> destinations = List.of(tube1, tube2);
        doReturn(destinations).when(planService).createDestinations(any());
        List<PlanAction> actions1 = List.of(new PlanAction(50, plans[0].getId(),
                tube1.getFirstSlot(), tube2.getFirstSlot(), EntityFactory.getSample(), null, null, null));
        List<PlanAction> actions2 = List.of(new PlanAction(51, plans[1].getId(),
                tube1.getFirstSlot(), tube2.getFirstSlot(), EntityFactory.getSample(), null, null, null));

        doReturn(actions1, actions2).when(planService).createActions(any(), anyInt(), any(), any(), any());
        BioState opBs = (useOpBs ? new BioState(2, "Alabama") : null);
        OperationType opType = EntityFactory.makeOperationType("Section", opBs, OperationTypeFlag.SOURCE_IS_BLOCK);
        when(mockOpTypeRepo.getByName("Section")).thenReturn(opType);

        final List<PlanRequestLabware> prlws = destinations.stream()
                .map(unused -> new PlanRequestLabware())
                .collect(toList());
        prlws.get(0).setCosting(SlideCosting.SGP);
        prlws.get(0).setLotNumber("lot1");
        prlws.get(1).setCosting(SlideCosting.Faculty);
        final PlanRequest request = new PlanRequest("Section", prlws);
        PlanResult result = planService.executePlanRequest(user, request);

        assertNotNull(result);
        assertThat(result.getOperations()).containsExactly(plans);
        assertThat(result.getLabware()).hasSameElementsAs(destinations);
        assertEquals(plans[0].getPlanActions(), actions1);
        assertEquals(plans[1].getPlanActions(), actions2);

        verify(planService, times(request.getLabware().size())).createPlan(user, opType);
        verify(planService).lookUpSources(request);
        verify(planService).createDestinations(request);
        verify(planService, times(request.getLabware().size())).createActions(any(), anyInt(), same(sources), any(), any());
        verify(planService).createActions(request.getLabware().get(0), plans[0].getId(), sources, destinations.get(0), opBs);
        verify(planService).createActions(request.getLabware().get(1), plans[1].getId(), sources, destinations.get(1), opBs);
        verify(mockLwNoteRepo).saveAll(List.of(
                LabwareNote.noteForPlan(destinations.get(0).getId(), plans[0].getId(), "costing", "SGP"),
                LabwareNote.noteForPlan(destinations.get(0).getId(), plans[0].getId(), "lot", "LOT1"),
                LabwareNote.noteForPlan(destinations.get(1).getId(), plans[1].getId(), "costing", "Faculty")
        ));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testExecutePlanRequest_fetalWaste(boolean useOpBs) {
        PlanOperation[] plans = IntStream.range(10, 13)
                .mapToObj(id -> {
                    PlanOperation plan = new PlanOperation();
                    plan.setId(id);
                    return plan;
                }).toArray(PlanOperation[]::new);
        doReturn(plans[0], plans[1], plans[2]).when(planService).createPlan(any(), any());

        BioState fwBs = new BioState(10, "Fetal waste");
        when(mockBsRepo.getByName("Fetal waste")).thenReturn(fwBs);

        UCMap<Labware> sources = UCMap.from(Labware::getBarcode,
                new Labware(1, "STAN-1A", EntityFactory.getTubeType(), null));
        doReturn(sources).when(planService).lookUpSources(any());

        final Labware tube1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        final Labware tube2 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        LabwareType fwLt = EntityFactory.makeLabwareType(1, 1);
        fwLt.setName(LabwareType.FETAL_WASTE_NAME);
        final Labware fwLw = EntityFactory.makeEmptyLabware(fwLt);
        List<Labware> destinations = List.of(tube1, tube2, fwLw);
        doReturn(destinations).when(planService).createDestinations(any());
        List<PlanAction> actions1 = List.of(new PlanAction(50, plans[0].getId(),
                tube1.getFirstSlot(), tube2.getFirstSlot(), EntityFactory.getSample(), null, null, null));
        List<PlanAction> actions2 = List.of(new PlanAction(51, plans[1].getId(),
                tube1.getFirstSlot(), tube2.getFirstSlot(), EntityFactory.getSample(), null, null, null));
        List<PlanAction> actions3 =  List.of(new PlanAction(52, plans[2].getId(),
                    tube1.getFirstSlot(), tube2.getFirstSlot(), EntityFactory.getSample(), null, null, null));

        doReturn(actions1, actions2, actions3).when(planService).createActions(any(), anyInt(), any(), any(), any());
        BioState opBs = (useOpBs ? new BioState(2, "Alabama") : null);
        OperationType opType = EntityFactory.makeOperationType("Section", opBs, OperationTypeFlag.SOURCE_IS_BLOCK);
        when(mockOpTypeRepo.getByName("Section")).thenReturn(opType);

        final List<PlanRequestLabware> prlws = destinations.stream()
                .map(unused -> new PlanRequestLabware())
                .collect(toList());
        final PlanRequest request = new PlanRequest("Section", prlws);
        PlanResult result = planService.executePlanRequest(user, request);

        assertNotNull(result);
        assertThat(result.getOperations()).containsExactly(plans);
        assertThat(result.getLabware()).hasSameElementsAs(destinations);
        assertEquals(plans[0].getPlanActions(), actions1);
        assertEquals(plans[1].getPlanActions(), actions2);
        assertEquals(plans[2].getPlanActions(), actions3);

        verify(planService, times(request.getLabware().size())).createPlan(user, opType);
        verify(planService).lookUpSources(request);
        verify(planService).createDestinations(request);
        verify(planService, times(request.getLabware().size())).createActions(any(), anyInt(), same(sources), any(), any());
        verify(planService).createActions(request.getLabware().get(0), plans[0].getId(), sources, destinations.get(0), opBs);
        verify(planService).createActions(request.getLabware().get(1), plans[1].getId(), sources, destinations.get(1), opBs);
        verify(planService).createActions(request.getLabware().get(2), plans[2].getId(), sources, destinations.get(2), fwBs);
        verify(mockLwNoteRepo, never()).saveAll(any());
    }

    @Test
    public void testCreatePlan() {
        OperationType opType = new OperationType(10, "Section");
        when(mockPlanRepo.save(any())).then(Matchers.returnArgument());

        PlanOperation plan = planService.createPlan(user, opType);

        assertEquals(plan.getOperationType(), opType);
        assertEquals(plan.getUser(), user);
        verify(mockPlanRepo).save(plan);
    }

    @Test
    public void testLookUpSources() {
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = List.of(EntityFactory.makeEmptyLabware(lt),
                EntityFactory.makeEmptyLabware(lt));
        UCMap<Labware> lwMap = UCMap.from(labware, Labware::getBarcode);
        when(mockLwRepo.getMapByBarcodeIn(any())).thenReturn(lwMap);
        Address FIRST = new Address(1, 1);
        PlanRequest request = new PlanRequest("Section",
                List.of(new PlanRequestLabware(lt.getName(), "STAN-99",
                        Stream.of(labware.get(0).getBarcode().toUpperCase(), labware.get(0).getBarcode().toLowerCase(),
                                labware.get(1).getBarcode())
                                .map(bc -> new PlanRequestSource(bc, FIRST))
                                .map(src -> new PlanRequestAction(FIRST, 1, src, null))
                                .collect(toList()))
                )
        );

        assertSame(lwMap, planService.lookUpSources(request));
    }

    @Test
    public void testCreateDestinations() {
        LabwareType tubeType = EntityFactory.getTubeType();
        LabwareType preType = EntityFactory.makeLabwareType(1, 1);
        preType.setPrebarcoded(true);
        final String prebarcode = "SPECIAL1";

        Stream.of(tubeType, preType).forEach(lt -> when(mockLtRepo.getByName(lt.getName())).thenReturn(lt));

        Labware tube1 = EntityFactory.makeEmptyLabware(tubeType);
        Labware tube2 = EntityFactory.makeEmptyLabware(tubeType);
        Labware pre = EntityFactory.makeEmptyLabware(preType);
        pre.setBarcode(prebarcode);
        List<Labware> lws = List.of(tube1, tube2, pre);

        when(mockLwService.create(tubeType)).thenReturn(tube1, tube2);
        when(mockLwService.create(preType, prebarcode, prebarcode)).thenReturn(pre);

        PlanRequest request = new PlanRequest("Section",
                Stream.of(null, null, prebarcode)
                        .map(bc -> new PlanRequestLabware(bc == null ? tubeType.getName() : preType.getName(),
                                bc, List.of()))
                        .collect(toList())
        );

        List<Labware> destinations = planService.createDestinations(request);
        assertEquals(lws, destinations);

        verify(mockLwService, times(2)).create(tubeType);
        verify(mockLwService).create(preType, prebarcode, prebarcode);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testCreateActions(boolean useNewBioState) {
        BioState bioState = (useNewBioState ? new BioState(2, "RNA") : null);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample nonSectionSample = EntityFactory.getSample();
        Sample sectionSample = new Sample(nonSectionSample.getId()+1, null, nonSectionSample.getTissue(), EntityFactory.getBioState());
        List<Sample> samples = List.of(sectionSample, nonSectionSample);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1, 2);
        List<Labware> sources = samples.stream()
                .map(sample -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    lw.getFirstSlot().getSamples().add(sample);
                    lw.getSlot(A2).getSamples().add(nonSectionSample);
                    return lw;
                })
                .collect(toList());
        List<String> sourceBarcodes = sources.stream().map(Labware::getBarcode).collect(toList());
        UCMap<Labware> sourceMap = UCMap.from(sources, Labware::getBarcode);
        Labware destination = EntityFactory.makeEmptyLabware(lt);
        int planId = 99;
        PlanRequestLabware prl =  new PlanRequestLabware(lt.getName(), destination.getBarcode(),
                List.of(
                        new PlanRequestAction(A1, samples.get(0).getId(),
                                new PlanRequestSource(sourceBarcodes.get(0), A1), null),
                        new PlanRequestAction(A2, samples.get(0).getId(),
                                new PlanRequestSource(sourceBarcodes.get(0), null), 1)
                ));

        List<PlanAction> expectedActions = List.of(
                new PlanAction(21, planId, sources.get(0).getFirstSlot(), destination.getFirstSlot(), samples.get(0), null, null, bioState),
                new PlanAction(22, planId, sources.get(0).getFirstSlot(), destination.getSlot(A2), samples.get(0), null, 1, bioState)
        );

        final int[] planActionIdCounter = {20};
        when(mockPlanActionRepo.saveAll(any())).then(invocation -> {
            Iterable<PlanAction> pas = invocation.getArgument(0);
            for (var pa : pas) {
                pa.setId(++planActionIdCounter[0]);
            }
            return pas;
        });

        final List<PlanAction> actions = planService.createActions(prl, planId, sourceMap, destination, bioState);
        assertEquals(expectedActions, actions);

        verify(mockPlanActionRepo).saveAll(actions);
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2})
    public void testGetPlanData(int numPlansFound) {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        final String barcode = lw.getBarcode();
        when(mockLwRepo.getByBarcode(barcode)).thenReturn(lw);
        OperationType opType = EntityFactory.makeOperationType("Section", null);
        List<PlanOperation> plans = IntStream.range(0, numPlansFound)
                .mapToObj(i -> new PlanOperation(100+i, opType, null, null, null))
                .collect(toList());
        doNothing().when(planService).validateLabwareForPlanData(any());

        when(mockPlanRepo.findAllByDestinationIdIn(any())).thenReturn(plans);

        if (numPlansFound != 1) {
            String expectedErrorMessage = String.format("%s found for labware %s.",
                    numPlansFound==0 ? "No plan" : "Multiple plans", barcode);
            assertThat(assertThrows(IllegalArgumentException.class, () -> planService.getPlanData(barcode)))
                    .hasMessage(expectedErrorMessage);
        } else {
            PlanOperation plan = plans.get(0);
            List<Labware> sources = List.of(EntityFactory.makeEmptyLabware(lt));
            doReturn(sources).when(planService).getSources(any());

            assertEquals(new PlanData(plan, sources, lw), planService.getPlanData(barcode));
            verify(planService).getSources(plan);
        }

        verify(mockLwRepo).getByBarcode(barcode);
        verify(planService).validateLabwareForPlanData(lw);
        verify(mockPlanRepo).findAllByDestinationIdIn(List.of(lw.getId()));
    }

    @ParameterizedTest
    @MethodSource("validateLabwareArgs")
    public void testValidateLabwareForPlanData(Labware labware, String expectedErrorMessage) {
        if (expectedErrorMessage==null) {
            planService.validateLabwareForPlanData(labware);
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> planService.validateLabwareForPlanData(labware)))
                    .hasMessage(expectedErrorMessage);
        }
    }

    static Stream<Arguments> validateLabwareArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] labware = IntStream.range(0,5).mapToObj(i -> {
            Labware lw = EntityFactory.makeEmptyLabware(lt);
            lw.setBarcode("STAN-10"+i);
            return lw;
        }).toArray(Labware[]::new);
        labware[1].getFirstSlot().getSamples().add(EntityFactory.getSample());
        labware[2].setDestroyed(true);
        labware[3].setReleased(true);
        labware[4].setDiscarded(true);
        return Arrays.stream(new Object[][] {
                { labware[0], null },
                { labware[1], "Labware STAN-101 already contains samples." },
                { labware[2], "Labware STAN-102 is destroyed." },
                { labware[3], "Labware STAN-103 is released." },
                { labware[4], "Labware STAN-104 is discarded." },
        }).map(Arguments::of);
    }

    @Test
    public void testGetSources() {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Labware[] labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample, sample))
                .toArray(Labware[]::new);
        Labware dest = EntityFactory.makeEmptyLabware(lt);
        OperationType opType = EntityFactory.makeOperationType("Section", null);
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        PlanOperation plan = EntityFactory.makePlanForSlots(opType,
                List.of(labware[0].getSlot(A1), labware[0].getSlot(A2), labware[1].getSlot(A1)),
                List.of(dest.getSlot(A1), dest.getSlot(A1), dest.getSlot(A2)),
                null);
        List<Labware> lwList = Arrays.asList(labware);
        doReturn(lwList).when(mockLwRepo).findAllByIdIn(any());
        assertEquals(lwList, planService.getSources(plan));
        verify(mockLwRepo).findAllByIdIn(Set.of(labware[0].getId(), labware[1].getId()));
    }
}
