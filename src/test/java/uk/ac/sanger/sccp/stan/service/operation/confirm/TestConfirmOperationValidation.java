package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationLabware;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationLabware.AddressCommentId;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ConfirmOperationValidationImp}
 * @author dr6
 */
public class TestConfirmOperationValidation {
    private LabwareRepo mockLabwareRepo;
    private PlanOperationRepo mockPlanOpRepo;
    private CommentRepo mockCommentRepo;

    @BeforeEach
    void setup() {
        mockLabwareRepo = mock(LabwareRepo.class);
        mockPlanOpRepo = mock(PlanOperationRepo.class);
        mockCommentRepo = mock(CommentRepo.class);
    }

    private ConfirmOperationValidationImp makeValidation() {
        return makeValidation(new ConfirmOperationRequest(List.of()));
    }

    private ConfirmOperationValidationImp makeValidation(ConfirmOperationLabware... requestLabware) {
        return makeValidation(new ConfirmOperationRequest(Arrays.asList(requestLabware)));
    }

    private ConfirmOperationValidationImp makeValidation(ConfirmOperationRequest request) {
        return spy(new ConfirmOperationValidationImp(request, mockLabwareRepo, mockPlanOpRepo, mockCommentRepo));
    }

    @Test
    public void testValidate() {
        Labware lw = EntityFactory.getTube();
        ConfirmOperationValidationImp validation = makeValidation(new ConfirmOperationLabware(lw.getBarcode()));
        Map<String, Labware> labwareMap = Map.of(lw.getBarcode(), lw);
        doReturn(labwareMap).when(validation).validateLabware();
        Map<Integer, PlanOperation> planMap = Map.of();
        doReturn(planMap).when(validation).lookUpPlans(any());
        doNothing().when(validation).validateOperations(any(), any());
        doNothing().when(validation).validateComments();

        Collection<String> problems = validation.validate();
        assertThat(problems).isEmpty();

        verify(validation).validateLabware();
        verify(validation).lookUpPlans(labwareMap.values());
        verify(validation).validateOperations(labwareMap, planMap);
        verify(validation).validateComments();
    }

    @Test
    public void testValidateEmpty() {
        ConfirmOperationValidationImp validation = makeValidation();
        assertThat(validation.validate()).containsOnly("No labware specified in request.");
        verify(validation, never()).validateLabware();
        verify(validation, never()).lookUpPlans(any());
        verify(validation, never()).validateOperations(any(), any());
        verify(validation, never()).validateComments();
    }

    @ParameterizedTest
    @MethodSource("labwareArguments")
    public void testValidateLabware(Collection<String> barcodes, Collection<Labware> labware, Collection<String> expectedProblems) {
        final Set<String> ucBarcodes = barcodes.stream()
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(toSet());
        final Map<String, Labware> expectedLabwareMap = labware.stream()
                .filter(lw -> ucBarcodes.contains(lw.getBarcode()))
                .collect(toMap(Labware::getBarcode, lw -> lw));
        doAnswer(invocation -> {
            String barcode = invocation.getArgument(0);
            return Optional.ofNullable(expectedLabwareMap.get(barcode.toUpperCase()));
        }).when(mockLabwareRepo).findByBarcode(anyString());

        ConfirmOperationRequest request = new ConfirmOperationRequest(
                barcodes.stream()
                        .map(ConfirmOperationLabware::new)
                        .collect(toList())
        );
        ConfirmOperationValidationImp validation = makeValidation(request);
        Map<String, Labware> labwareMap = validation.validateLabware();
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
        assertThat(labwareMap).isEqualTo(expectedLabwareMap);
    }

    static Stream<Arguments> labwareArguments() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware emptyTube = EntityFactory.makeEmptyLabware(lt);
        Labware discardedTube = EntityFactory.makeEmptyLabware(lt);
        discardedTube.setDiscarded(true);
        Labware destroyedTube = EntityFactory.makeEmptyLabware(lt);
        destroyedTube.setDestroyed(true);
        Labware releasedTube = EntityFactory.makeEmptyLabware(lt);
        releasedTube.setReleased(true);
        Labware usedTube = EntityFactory.makeEmptyLabware(lt);
        usedTube.getFirstSlot().getSamples().add(EntityFactory.getSample());

        String emptyBc = emptyTube.getBarcode();
        String discardedBc = discardedTube.getBarcode();
        String usedBc = usedTube.getBarcode();
        String destroyedBc = destroyedTube.getBarcode();
        String releasedBc = releasedTube.getBarcode();
        String invalidBc = "NOSUCHBARCODE";

        String discardedProblem = "Labware " + discardedBc + " is already discarded.";
        String destroyedProblem = "Labware " + destroyedBc + " is destroyed.";
        String releasedProblem = "Labware " + releasedBc + " is released.";

        String usedProblem = "Labware " + usedBc + " already has contents.";
        String invalidProblem = "Unknown labware barcode: " + invalidBc;

        return Stream.of(
                Arguments.of(List.of(emptyBc), List.of(emptyTube), List.of()),
                Arguments.of(List.of(discardedBc), List.of(discardedTube), List.of(discardedProblem)),
                Arguments.of(List.of(usedBc), List.of(usedTube), List.of(usedProblem)),
                Arguments.of(List.of(releasedBc), List.of(releasedTube), List.of(releasedProblem)),
                Arguments.of(List.of(destroyedBc), List.of(destroyedTube), List.of(destroyedProblem)),
                Arguments.of(List.of(invalidBc), List.of(), List.of(invalidProblem)),
                Arguments.of(List.of(emptyBc, emptyBc), List.of(emptyTube), List.of("Repeated labware barcode: "+emptyBc)),
                Arguments.of(Arrays.asList(emptyBc, null), List.of(emptyTube), List.of("Missing labware barcode.")),
                Arguments.of(List.of(emptyBc, ""), List.of(emptyTube), List.of("Missing labware barcode.")),
                Arguments.of(List.of(emptyBc, discardedBc, usedBc, invalidBc, destroyedBc, releasedBc),
                        List.of(emptyTube, discardedTube, usedTube, destroyedTube, releasedTube),
                        List.of(discardedProblem, usedProblem, invalidProblem, destroyedProblem, releasedProblem))
        );
    }

    @Test
    public void testLookUpPlans() {
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0, 3).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).collect(toList());
        Labware otherLabware = EntityFactory.makeEmptyLabware(lt);
        Labware source = EntityFactory.makeLabware(lt, EntityFactory.getSample());
        OperationType opType = EntityFactory.makeOperationType("Section");
        PlanOperation plan1 = EntityFactory.makePlanForLabware(opType, List.of(source), List.of(labware.get(0), otherLabware));
        PlanOperation plan2 = EntityFactory.makePlanForLabware(opType, List.of(source), labware.subList(0,2));
        when(mockPlanOpRepo.findAllByDestinationIdIn(any())).thenReturn(List.of(plan1, plan2));

        ConfirmOperationValidationImp validation = makeValidation();

        Map<Integer, PlanOperation> planMap = validation.lookUpPlans(labware);
        assertThat(planMap).isEqualTo(Map.of(labware.get(1).getId(), plan2));
        assertThat(validation.getProblems()).containsOnly("Multiple plans found for labware "+labware.get(0).getBarcode(),
                "No plan found for labware "+labware.get(2).getBarcode());

        verify(mockPlanOpRepo).findAllByDestinationIdIn(labware.stream().map(Labware::getId).collect(toSet()));
    }

    @Test
    public void testValidateOperations() {
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = IntStream.range(0, 2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).collect(toList());
        Labware source = EntityFactory.makeLabware(lt, EntityFactory.getSample());
        OperationType opType = EntityFactory.makeOperationType("Section");
        PlanOperation plan = EntityFactory.makePlanForLabware(opType, List.of(source), labware.subList(0, 1));
        Map<Integer, PlanOperation> planMap = Map.of(labware.get(0).getId(), plan);

        ConfirmOperationRequest request = new ConfirmOperationRequest(
                Stream.concat(labware.stream().map(Labware::getBarcode), Stream.of("NOSUCHLABWARE", null, ""))
                        .map(ConfirmOperationLabware::new)
                        .collect(toList())
        );

        ConfirmOperationValidationImp validation = makeValidation(request);

        doNothing().when(validation).validateAddresses(any(), any(), any());

        validation.validateOperations(bcMap(labware), planMap);

        verify(validation).validateAddresses(request.getLabware().get(0), labware.get(0), plan);
    }

    @Test
    public void testValidateAddresses() {
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        String bc = lw.getBarcode();
        Labware otherLabware = EntityFactory.makeEmptyLabware(lt);
        Labware source = EntityFactory.makeLabware(EntityFactory.getTubeType(), EntityFactory.getSample());
        OperationType opType = EntityFactory.makeOperationType("Section");
        PlanOperation plan = EntityFactory.makePlanForSlots(opType, List.of(source.getFirstSlot()),
                List.of(lw.getFirstSlot(), otherLabware.getFirstSlot(), otherLabware.getSlots().get(1)),
                null);
        List<Address> cancelledAddresses = List.of(new Address(1,1), new Address(1,2),
                new Address(2,1));
        List<AddressCommentId> addressCommentIds = List.of(
                new AddressCommentId(new Address(1,1), 1),
                new AddressCommentId(new Address(1,2), 2),
                new AddressCommentId(new Address(2,3), 3),
                new AddressCommentId(null, 4)
        );
        ConfirmOperationLabware col = new ConfirmOperationLabware(lw.getBarcode(), false, cancelledAddresses,
                addressCommentIds);

        ConfirmOperationValidationImp validation = makeValidation();
        validation.validateAddresses(col, lw, plan);

        assertThat(validation.getProblems()).containsOnly(
                "Invalid address B1 in cancelled addresses for labware "+bc+".",
                "No planned action recorded for address A2 in labware "+bc+", specified as cancelled.",
                "Comment specified with no address for labware "+bc+".",
                "Invalid address B3 in comments for labware "+bc+".",
                "No planned action recorded for address A2 in labware "+bc+", specified in comments."
        );
    }

    @Test
    public void testValidateComments() {
        ConfirmOperationValidationImp validation = makeValidation(
                new ConfirmOperationLabware("STAN-A", false, List.of(),
                        List.of(new AddressCommentId(new Address(1,1), 1),
                                new AddressCommentId(new Address(1,2), 1),
                                new AddressCommentId(new Address(1,2), 2))),
                new ConfirmOperationLabware("STAN-B", false, List.of(),
                        List.of(new AddressCommentId(new Address(1,1), 2),
                                new AddressCommentId(new Address(1,1), 3),
                                new AddressCommentId(new Address(1,2), null)))
        );

        Set<Integer> idsGivenAsArgument = new HashSet<>();
        when(mockCommentRepo.findIdByIdIn(any())).then(invocation -> {
            Collection<Integer> ids = invocation.getArgument(0);
            idsGivenAsArgument.addAll(ids);
            return Set.of(1,2);
        });

        validation.validateComments();
        assertThat(validation.getProblems()).containsOnly(
                "Null given as ID for comment.",
                "Unknown comment IDs: "+List.of(3)
        );
        verify(mockCommentRepo).findIdByIdIn(any());
        // verify(mockCommentRepo).findIdByIdIn(Set.of(1,2,3)); -- doesn't work because the method under test modifies the set
        assertEquals(Set.of(1, 2, 3), idsGivenAsArgument);
    }

    @Test
    public void testValidateNoComments() {
        ConfirmOperationValidationImp validation = makeValidation(
                new ConfirmOperationLabware("STAN-A")
        );
        validation.validateComments();
        assertThat(validation.getProblems()).isEmpty();
        verify(mockCommentRepo, never()).findIdByIdIn(any());
    }

    private static Map<String, Labware> bcMap(Collection<Labware> labware) {
        return labware.stream()
                .collect(toMap(Labware::getBarcode, lw -> lw));
    }
}
