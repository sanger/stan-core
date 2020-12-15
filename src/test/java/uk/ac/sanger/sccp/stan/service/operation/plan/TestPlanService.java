package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.*;
import uk.ac.sanger.sccp.stan.service.*;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.eqCi;

/**
 * Tests for {@link PlanServiceImp}
 * @author dr6
 */
public class TestPlanService {
    private PlanServiceImp planService;

    private PlanValidationFactory mockPlanValidationFactory;
    private PlanValidation mockPlanValidation;
    private LabwareService mockLwService;
    private SampleService mockSampleService;

    private PlanOperationRepo mockPlanRepo;
    private PlanActionRepo mockPlanActionRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLwRepo;
    private LabwareTypeRepo mockLtRepo;

    private User user;

    @BeforeEach
    void setup() {
        mockPlanValidationFactory = mock(PlanValidationFactory.class);
        mockPlanValidation = mock(PlanValidation.class);
        mockLwService = mock(LabwareService.class);
        mockSampleService = mock(SampleService.class);
        mockPlanRepo = mock(PlanOperationRepo.class);
        mockPlanActionRepo = mock(PlanActionRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockLtRepo = mock(LabwareTypeRepo.class);

        user = EntityFactory.getUser();

        when(mockPlanValidationFactory.createPlanValidation(any())).thenReturn(mockPlanValidation);

        planService = spy(new PlanServiceImp(mockPlanValidationFactory, mockLwService, mockSampleService, mockPlanRepo,
                mockPlanActionRepo, mockOpTypeRepo, mockLwRepo, mockLtRepo));
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

    @Test
    public void testExecutePlanRequest() {
        PlanOperation plan = new PlanOperation();
        plan.setId(10);
        doReturn(plan).when(planService).createPlan(any(), any());
        Map<String, Labware> sources = Map.of("STAN-1A",
                new Labware(1, "STAN-1A", EntityFactory.getTubeType(), null));
        doReturn(sources).when(planService).lookUpSources(any());
        final Labware tube = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        List<Labware> destinations = List.of(tube);
        doReturn(destinations).when(planService).createDestinations(any());
        List<PlanAction> actions = List.of(new PlanAction(50, plan.getId(),
                tube.getFirstSlot(), tube.getFirstSlot(), EntityFactory.getSample(), null, null));
        doReturn(actions).when(planService).createActions(any(), anyInt(), any(), any());

        final PlanRequest request = new PlanRequest("Section", List.of());
        PlanResult result = planService.executePlanRequest(user, request);

        assertNotNull(result);
        assertThat(result.getOperations()).containsOnly(plan);
        assertThat(result.getLabware()).hasSameElementsAs(destinations);
        assertEquals(plan.getPlanActions(), actions);

        verify(planService).createPlan(user, request.getOperationType());
        verify(planService).lookUpSources(request);
        verify(planService).createDestinations(request);
        verify(planService).createActions(request, plan.getId(), sources, destinations);
    }

    @Test
    public void testCreatePlan() {
        OperationType opType = new OperationType(10, "Section");
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);

        PlanOperation plan = planService.createPlan(user, opType.getName());

        assertEquals(plan.getOperationType(), opType);
        assertEquals(plan.getUser(), user);
        verify(mockPlanRepo).save(plan);
    }

    @Test
    public void testLookUpSources() {
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = List.of(EntityFactory.makeEmptyLabware(lt),
                EntityFactory.makeEmptyLabware(lt));
        labware.forEach(lw -> when(mockLwRepo.getByBarcode(eqCi(lw.getBarcode()))).thenReturn(lw));
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

        Map<String, Labware> labwareMap = planService.lookUpSources(request);
        assertEquals(Map.of(labware.get(0).getBarcode(), labware.get(0),
                labware.get(1).getBarcode(), labware.get(1)),
                labwareMap);
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
        when(mockLwService.create(preType, prebarcode)).thenReturn(pre);

        PlanRequest request = new PlanRequest("Section",
                Stream.of(null, null, prebarcode)
                        .map(bc -> new PlanRequestLabware(bc == null ? tubeType.getName() : preType.getName(),
                                bc, List.of()))
                        .collect(toList())
        );

        List<Labware> destinations = planService.createDestinations(request);
        assertEquals(lws, destinations);

        verify(mockLwService, times(2)).create(tubeType);
        verify(mockLwService).create(preType, prebarcode);
    }

    @Test
    public void testCreateActions() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample nonSectionSample = EntityFactory.getSample();
        Sample sectionSample = new Sample(nonSectionSample.getId()+1, null, nonSectionSample.getTissue());
        List<Sample> samples = List.of(sectionSample, nonSectionSample);
        final Address FIRST = new Address(1,1);
        final Address SECOND = new Address(1, 2);
        List<Labware> sources = samples.stream()
                .map(sample -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    lw.getFirstSlot().getSamples().add(sample);
                    lw.getSlot(SECOND).getSamples().add(nonSectionSample);
                    return lw;
                })
                .collect(toList());
        List<String> sourceBarcodes = sources.stream().map(Labware::getBarcode).collect(toList());
        Map<String, Labware> sourceMap = sources.stream()
                .collect(toMap(Labware::getBarcode, lw -> lw));
        List<Labware> destinations = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .collect(toList());
        int planId = 99;
        PlanRequest request = new PlanRequest("Section",
                List.of(
                        new PlanRequestLabware(lt.getName(), destinations.get(0).getBarcode(),
                                List.of(
                                        new PlanRequestAction(FIRST, samples.get(0).getId(),
                                                new PlanRequestSource(sourceBarcodes.get(0), FIRST), null),
                                        new PlanRequestAction(SECOND, samples.get(0).getId(),
                                                new PlanRequestSource(sourceBarcodes.get(0), null), 1)
                                )),
                        new PlanRequestLabware(lt.getName(), destinations.get(1).getBarcode(),
                                List.of(
                                        new PlanRequestAction(FIRST, samples.get(0).getId(),
                                                new PlanRequestSource(sourceBarcodes.get(0), null), 2),
                                        new PlanRequestAction(SECOND, samples.get(1).getId(),
                                                new PlanRequestSource(sourceBarcodes.get(1), SECOND), 3)
                                ))
                ));

        List<PlanAction> expectedActions = List.of(
                new PlanAction(21, planId, sources.get(0).getFirstSlot(), destinations.get(0).getFirstSlot(), samples.get(0), 5, null),
                new PlanAction(22, planId, sources.get(0).getFirstSlot(), destinations.get(0).getSlot(SECOND), samples.get(0), 6, 1),
                new PlanAction(23, planId, sources.get(0).getFirstSlot(), destinations.get(1).getFirstSlot(), samples.get(0), 7, 2),
                new PlanAction(24, planId, sources.get(1).getSlot(SECOND), destinations.get(1).getSlot(SECOND), samples.get(1), null, 3)
        );

        final int[] planActionIdCounter = {20};
        when(mockPlanActionRepo.save(any())).then(invocation -> {
            PlanAction plac = invocation.getArgument(0);
            plac.setId(++planActionIdCounter[0]);
            return plac;
        });

        final int[] newSectionCounter = {4};
        when(mockSampleService.nextSection(any())).then(invocation -> (++newSectionCounter[0]));

        final List<PlanAction> actions = planService.createActions(request, planId, sourceMap, destinations);
        assertEquals(expectedActions, actions);

        actions.forEach(ac -> verify(mockPlanActionRepo).save(ac));
    }
}
