package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.*;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.eqCi;

/**
 * Tests {@link PlanValidationImp}
 * @author dr6
 */
public class TestPlanValidation {
    private LabwareRepo mockLabwareRepo;
    private LabwareTypeRepo mockLabwareTypeRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private Validator<String> mockPrebarcodeValidator;

    @BeforeEach
    void setup() {
        mockLabwareRepo = mock(LabwareRepo.class);
        mockLabwareTypeRepo = mock(LabwareTypeRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        //noinspection unchecked
        mockPrebarcodeValidator = mock(Validator.class);
    }

    private PlanValidationImp makeValidation(PlanRequest request) {
        return spy(new PlanValidationImp(request, mockLabwareRepo, mockLabwareTypeRepo,
                mockOpTypeRepo, mockPrebarcodeValidator));
    }

    @Test
    public void testValidate() {
        PlanRequest request = new PlanRequest();
        PlanValidationImp validation = makeValidation(request);
        final OperationType opType = new OperationType(1, "Stir fry");
        doReturn(opType).when(validation).validateOperation();
        doNothing().when(validation).validateSources(any());
        doNothing().when(validation).validateDestinations();

        assertSame(validation.validate(), validation.problems);

        verify(validation).validateOperation();
        verify(validation).validateSources(opType);
        verify(validation).validateDestinations();
    }

    @Test
    public void testValidateOperation() {
        OperationType sectionOpType = EntityFactory.makeOperationType("Section", OperationTypeFlag.SOURCE_IS_BLOCK);
        OperationType registerOpType = EntityFactory.makeOperationType("Register", OperationTypeFlag.IN_PLACE);
        final List<OperationType> opTypes = List.of(sectionOpType, registerOpType);
        when(mockOpTypeRepo.findByName(anyString())).then(invocation -> {
            final String name = invocation.getArgument(0);
            return opTypes.stream()
                    .filter(t -> name.equalsIgnoreCase(t.getName()))
                    .findAny();
        });

        Object[] testData = {
                null, null, "No operation type specified.",
                "", null, "No operation type specified.",
                "Bananas", null, "Unknown operation type: Bananas",
                registerOpType.getName(), registerOpType, "You cannot prelabel for operation type Register.",
                sectionOpType.getName(), sectionOpType, null
        };

        for (int i = 0; i < testData.length; i += 3) {
            PlanRequest request = new PlanRequest();
            request.setOperationType((String) testData[i]);
            PlanValidationImp validation = makeValidation(request);
            assertEquals(validation.validateOperation(), testData[i+1]);
            String problem = (String) testData[i+2];
            if (problem==null) {
                assertThat(validation.problems).isEmpty();
            } else {
                assertThat(validation.problems).containsOnly(problem);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sourcesData")
    public void testValidateSources(Object sourceBarcodes, Object sourceAddresses, Integer sampleId,
                                    Labware existingLabware, OperationType opType, Object expectedProblems) {
        when(mockLabwareRepo.findByBarcode(any())).thenReturn(Optional.empty());
        if (existingLabware!=null) {
            doReturn(Optional.of(existingLabware))
                    .when(mockLabwareRepo).findByBarcode(eqCi(existingLabware.getBarcode()));
        }
        Stream<String> sourceBarcodeStream = toStream(sourceBarcodes);
        Iterator<Address> sourceAddressIter = toIterator(sourceAddresses);
        List<PlanRequestAction> prActions = sourceBarcodeStream
                    .map(bc -> new PlanRequestAction(
                            new Address(1, 1), sampleId,
                            new PlanRequestSource(bc, sourceAddressIter.hasNext() ? sourceAddressIter.next() : null), null)
                    )
                    .collect(Collectors.toList());

        PlanRequest request = new PlanRequest(
                opType.getName(),
                List.of(new PlanRequestLabware("Tube", null, prActions))
        );

        PlanValidationImp validation = makeValidation(request);
        validation.validateSources(opType);

        assertProblems(expectedProblems, validation.problems);
    }

    @Test
    public void testValidateSourcesNoLabware() {
        PlanRequest request = new PlanRequest("Section", List.of());
        PlanValidationImp validation = makeValidation(request);
        validation.validateSources(EntityFactory.makeOperationType("Section"));
        assertThat(validation.problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("actionsData")
    public void testCheckActions(String barcode, Object planRequestActions, LabwareType labwareType, Object expectedProblems) {
        PlanRequestLabware prlw = new PlanRequestLabware(labwareType==null ? null : labwareType.getName(), barcode,
                objToList(planRequestActions));

        PlanValidationImp validation = makeValidation(new PlanRequest());
        validation.checkActions(prlw, labwareType);

        assertProblems(expectedProblems, validation.problems);
    }

    @ParameterizedTest
    @MethodSource("destinationData")
    public void testValidateDestinations(Object planRequestLabware,
                                         List<LabwareType> labwareTypes, Object expectedProblems) {
        PlanRequest request = new PlanRequest("opType", objToList(planRequestLabware));

        PlanValidationImp validation = makeValidation(request);
        doNothing().when(validation).checkActions(any(), any());

        when(mockLabwareTypeRepo.findByName(anyString())).thenReturn(Optional.empty());
        if (labwareTypes!=null && !labwareTypes.isEmpty()) {
            labwareTypes.forEach(lt ->
                    doReturn(Optional.of(lt)).when(mockLabwareTypeRepo).findByName(eqCi(lt.getName()))
            );
        }

        when(mockPrebarcodeValidator.validate(anyString(), any())).then(invocation -> {
            String string = invocation.getArgument(0);
            if (string.contains("*")) {
                Consumer<String> addProblem = invocation.getArgument(1);
                addProblem.accept("Invalid barcode: "+string);
                return false;
            }
            return true;
        });

        if (request.getLabware().stream().anyMatch(lw -> "extant".equalsIgnoreCase(lw.getBarcode()))) {
            when(mockLabwareRepo.existsByBarcode(eqCi("extant"))).thenReturn(true);
        }

        validation.validateDestinations();

        assertProblems(expectedProblems, validation.problems);

        if (expectedProblems==null) {
            verify(validation, times(request.getLabware().size())).checkActions(any(), any());
            for (PlanRequestLabware prlw : request.getLabware()) {
                if (prlw.getBarcode()!=null) {
                    verify(mockPrebarcodeValidator).validate(eqCi(prlw.getBarcode()), any());
                }
            }
        }
    }


    static Stream<Arguments> sourcesData() {
        Sample sample = EntityFactory.getSample();
        int sectionSampleId = sample.getId();
        int blockSampleId = sectionSampleId + 50;
        Sample blockSample = new Sample(blockSampleId, null, sample.getTissue(), EntityFactory.getBioState());
        LabwareType lt = EntityFactory.getTubeType();
        Labware block = EntityFactory.makeEmptyLabware(lt);
        Slot blockSlot = block.getFirstSlot();
        blockSlot.getSamples().add(blockSample);
        blockSlot.setBlockSampleId(blockSampleId);
        blockSlot.setBlockHighestSection(0);

        Labware nonBlock = EntityFactory.makeEmptyLabware(lt);
        nonBlock.getFirstSlot().getSamples().add(sample);

        Labware destroyedLw = EntityFactory.makeLabware(lt, sample);
        destroyedLw.setDestroyed(true);

        Labware releasedLw = EntityFactory.makeLabware(lt, sample);
        releasedLw.setReleased(true);

        Labware discardedLw = EntityFactory.makeLabware(lt, sample);
        discardedLw.setDiscarded(true);

        Address A1 = new Address(1,1);

        OperationType sectionOpType = EntityFactory.makeOperationType("Section", OperationTypeFlag.SOURCE_IS_BLOCK);
        OperationType nonSectionOpType = mock(OperationType.class);
        when(nonSectionOpType.getName()).thenReturn("Nonsection");
        when(nonSectionOpType.canPrelabel()).thenReturn(true);
        when(nonSectionOpType.canCreateSection()).thenReturn(false);
        OperationType otherOpType = EntityFactory.makeOperationType("Other");

        return Stream.of(
                Arguments.of(List.of(), List.of(), null, null, sectionOpType, null),
                Arguments.of(block.getBarcode(), A1, blockSampleId, block, sectionOpType, null),
                Arguments.of(block.getBarcode(), null, blockSampleId, block, sectionOpType, null),
                Arguments.of(nonBlock.getBarcode(), null, sectionSampleId, nonBlock, otherOpType, null),

                Arguments.of(destroyedLw.getBarcode(), A1, sectionSampleId, destroyedLw, otherOpType, "Labware already destroyed: ["+destroyedLw.getBarcode()+"]"),
                Arguments.of(releasedLw.getBarcode(), A1, sectionSampleId, releasedLw, otherOpType, "Labware already released: ["+releasedLw.getBarcode()+"]"),
                Arguments.of(discardedLw.getBarcode(), A1, sectionSampleId, discardedLw, otherOpType, "Labware already discarded: ["+discardedLw.getBarcode()+"]"),
                Arguments.of(null, A1, blockSampleId, block, sectionOpType, "Missing source barcode."),
                Arguments.of("", A1, blockSampleId, block, sectionOpType, "Missing source barcode."),
                Arguments.of("404", A1, blockSampleId, block, sectionOpType, "Unknown labware barcode: [404]"),
                Arguments.of(block.getBarcode(), new Address(2,3), blockSampleId, block, sectionOpType,
                        "Labware "+block.getBarcode()+" ("+lt.getName()+") has no slot at address B3."),
                Arguments.of(block.getBarcode(), null, blockSampleId+1, block, sectionOpType,
                        "Slot A1 of labware "+block.getBarcode()+" does not contain a sample with ID "+(blockSampleId+1)+"."),
                Arguments.of(block.getBarcode(), null, blockSampleId, block, nonSectionOpType,
                        "Operation Nonsection cannot create a section of sample "+blockSampleId+"."),
                Arguments.of(nonBlock.getBarcode(), null, sectionSampleId, nonBlock, sectionOpType,
                        "Source "+nonBlock.getBarcode()+",A1 is not a block for operation "+sectionOpType.getName()+"."),
                Arguments.of(List.of("404", "404"), List.of(), blockSampleId, null, sectionOpType, "Unknown labware barcode: [404]"),
                Arguments.of(List.of("404", "405"), List.of(), blockSampleId, null, sectionOpType, "Unknown labware barcodes: [404, 405]")
        );
    }

    static Stream<Arguments> actionsData() {
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        final Address FIRST = new Address(1,1);
        final Address SECOND = new Address(1,2);
        PlanRequestSource src = new PlanRequestSource("STAN-000", FIRST);
        PlanRequestSource srcAlt = new PlanRequestSource("stan-100", FIRST);
        PlanRequestSource src2 = new PlanRequestSource("STAN-001", FIRST);
        PlanRequestSource src3 = new PlanRequestSource("STAN-000", SECOND);
        return Stream.of(
                Arguments.of("STAN-100", List.of(new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(FIRST, 5, src, null),
                        new PlanRequestAction(SECOND, 4, src, null),
                        new PlanRequestAction(FIRST, 4, src2, null),
                        new PlanRequestAction(FIRST, 4, src3, null)),
                        lt, null),
                Arguments.of(null, List.of(new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(FIRST, 5, src, null),
                        new PlanRequestAction(SECOND, 4, src, null),
                        new PlanRequestAction(FIRST, 4, src2, null),
                        new PlanRequestAction(FIRST, 4, src3, null)),
                        lt, null),

                Arguments.of("STAN-100", List.of(), lt, "No actions specified for labware STAN-100."),
                Arguments.of(null, List.of(), lt, "No actions specified for labware of type "+lt.getName()+"."),
                Arguments.of(null, List.of(), null, "No actions specified for labware of unspecified type."),
                Arguments.of("STAN-100", List.of(new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(SECOND, 4, srcAlt, null)),
                        lt, "Actions for labware STAN-100 contain duplicate action: (address=A1, sampleId=4, source={STAN-000, A1})"),
                Arguments.of(null, List.of(new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(SECOND, 4, src, null)),
                        lt, "Actions for labware of type "+lt.getName()+" contain duplicate action: (address=A1, sampleId=4, source={STAN-000, A1})"),
                Arguments.of("STAN-100", new PlanRequestAction(null, 4, src, null), lt,
                        "Missing destination address."),
                Arguments.of(null, new PlanRequestAction(null, 4, src, null), lt,
                        "Missing destination address."),
                Arguments.of("STAN-100", new PlanRequestAction(new Address(2,4), 4, src, null), lt,
                        "Invalid address B4 given for labware type "+lt.getName()+"."),
                Arguments.of(null, new PlanRequestAction(new Address(4,7), 4, src, null), lt,
                        "Invalid address D7 given for labware type "+lt.getName()+".")
        );
    }

    static Stream<Arguments> destinationData() {
        LabwareType lt = EntityFactory.getTubeType();
        String ltName = lt.getName();
        LabwareType preLt = new LabwareType(2, "pre", 1, 1, EntityFactory.getLabelType(), true);
        String preName = preLt.getName();
        List<LabwareType> lts = List.of(lt, preLt);
        List<PlanRequestAction> noActions = List.of();

        return Stream.of(
                Arguments.of(List.of(new PlanRequestLabware(ltName, null, noActions),
                        new PlanRequestLabware(ltName, null, noActions),
                        new PlanRequestLabware(preName, "SPECIALBARCODE", noActions)),
                        lts, null),

                Arguments.of(List.of(), null, "No labware are specified in the plan request."),
                Arguments.of(List.of(new PlanRequestLabware(preName, "SPECIAL1", noActions),
                        new PlanRequestLabware(preName, "SPECIAL1", noActions)),
                        lts, "Repeated barcode given for new labware: SPECIAL1"),
                Arguments.of(new PlanRequestLabware(null, null, noActions),
                        lts, "Missing labware type."),
                Arguments.of(new PlanRequestLabware(preName, "EXTANT", noActions),
                        lts, "Labware with the barcode EXTANT already exists in the database."),
                Arguments.of(new PlanRequestLabware(preName, null, noActions),
                        lts, "No barcode supplied for new labware of type "+preName+"."),
                Arguments.of(new PlanRequestLabware(ltName, "SPECIAL1", noActions),
                        lts, "Unexpected barcode supplied for new labware of type "+ltName+"."),
                Arguments.of(new PlanRequestLabware("Mist", null, noActions),
                        lts, "Unknown labware type: [MIST]"),
                Arguments.of(List.of(
                        new PlanRequestLabware("Mist", null, noActions),
                        new PlanRequestLabware("MIST", null, noActions),
                        new PlanRequestLabware("Fog", null, noActions)
                ), lts, "Unknown labware types: [MIST, FOG]"),
                Arguments.of(new PlanRequestLabware(preName, "SPECIAL*", noActions),
                        lts, "Invalid barcode: SPECIAL*")

        );
    }

    private static void assertProblems(Object expectedProblems, Collection<String> problems) {
        if (expectedProblems == null) {
            assertThat(problems).isEmpty();
        } else if (expectedProblems instanceof String) {
            assertThat(problems).containsOnly((String) expectedProblems);
        } else {
            //noinspection unchecked
            assertThat(problems).hasSameElementsAs((Iterable<String>) expectedProblems);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> List<E> objToList(Object obj) {
        if (obj instanceof List) {
            return (List<E>) obj;
        }
        return (List<E>) singletonList(obj);
    }

    private static <E> Stream<E> toStream(Object obj) {
        return TestPlanValidation.<E>objToList(obj).stream();
    }

    private static <E> Iterator<E> toIterator(Object obj) {
        return TestPlanValidation.<E>objToList(obj).iterator();
    }
}
