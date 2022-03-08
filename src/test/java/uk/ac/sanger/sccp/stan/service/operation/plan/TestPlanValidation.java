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
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.EntityFactory.nullableObjToList;
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
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> sourceLwMap = UCMap.from(Labware::getBarcode, lw);
        doReturn(sourceLwMap).when(validation).validateSources(any());
        doNothing().when(validation).validateDestinations(sourceLwMap);

        assertSame(validation.validate(), validation.problems);

        verify(validation).validateOperation();
        verify(validation).validateSources(opType);
        verify(validation).validateDestinations(sourceLwMap);
    }

    @Test
    public void testValidateOperation() {
        OperationType sectionOpType = EntityFactory.makeOperationType("Section", null, OperationTypeFlag.SOURCE_IS_BLOCK);
        OperationType registerOpType = EntityFactory.makeOperationType("Register", null, OperationTypeFlag.IN_PLACE);
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
                    .collect(toList());

        PlanRequest request = new PlanRequest(
                opType.getName(),
                List.of(new PlanRequestLabware("Tube", null, prActions))
        );

        PlanValidationImp validation = makeValidation(request);
        Map<String, Labware> result = validation.validateSources(opType);

        assertProblems(expectedProblems, validation.problems);
        if (existingLabware!=null) {
            assertThat(result).containsEntry(existingLabware.getBarcode(), existingLabware).hasSize(1);
        }
    }

    @Test
    public void testValidateSourcesNoLabware() {
        PlanRequest request = new PlanRequest("Section", List.of());
        PlanValidationImp validation = makeValidation(request);
        validation.validateSources(EntityFactory.makeOperationType("Section", null));
        assertThat(validation.problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("actionsData")
    public void testCheckActions(String barcode, Object planRequestActions, LabwareType labwareType, Object expectedProblems) {
        final List<PlanRequestAction> placs = nullableObjToList(planRequestActions);
        PlanRequestLabware prlw = new PlanRequestLabware(labwareType==null ? null : labwareType.getName(), barcode, placs);

        PlanValidationImp validation = makeValidation(new PlanRequest());
        validation.checkActions(prlw, labwareType);

        assertProblems(expectedProblems, validation.problems);
    }

    @ParameterizedTest
    @MethodSource("destinationData")
    public void testValidateDestinations(Object planRequestLabware,
                                         List<LabwareType> labwareTypes, Object expectedProblems,
                                         Boolean dividedLayout) {
        PlanRequest request = new PlanRequest("opType", nullableObjToList(planRequestLabware));

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
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> sourceLwMap = UCMap.from(Labware::getBarcode, lw);
        if (dividedLayout!=null) {
            doReturn(dividedLayout).when(validation).hasDividedLayout(any(), any(), any(), anyInt());
        }
        validation.validateDestinations(sourceLwMap);
        if (dividedLayout==null) {
            verify(validation, never()).hasDividedLayout(any(), any(), any(), anyInt());
        } else {
            assert labwareTypes!=null;
            UCMap<LabwareType> adhLts = labwareTypes.stream()
                    .filter(lwt -> lwt.getLabelType()!=null && "adh".equalsIgnoreCase(lwt.getLabelType().getName()))
                    .collect(UCMap.toUCMap(LabwareType::getName));
            List<PlanRequestLabware> adhPrls = request.getLabware().stream()
                    .filter(prl -> adhLts.get(prl.getLabwareType())!=null)
                    .collect(toList());
            verify(validation, times(adhPrls.size())).hasDividedLayout(any(), any(), any(), anyInt());
            for (PlanRequestLabware prl : adhPrls) {
                final LabwareType lt = adhLts.get(prl.getLabwareType());
                verify(validation).hasDividedLayout(sourceLwMap, prl, lt, 1);
            }
        }

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

    @ParameterizedTest
    @MethodSource("hasDividedLayoutData")
    public void testHasDividedLayout(UCMap<Labware> sourceLwMap, PlanRequestLabware prl, LabwareType lt,
                                     int rowsPerGroup, boolean expected) {
        PlanRequest request = new PlanRequest("opType", List.of(prl));
        PlanValidationImp validation = makeValidation(request);
        assertEquals(expected, validation.hasDividedLayout(sourceLwMap, prl, lt, rowsPerGroup));
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

        OperationType sectionOpType = EntityFactory.makeOperationType("Section", null, OperationTypeFlag.SOURCE_IS_BLOCK);
        OperationType nonSectionOpType = mock(OperationType.class);
        when(nonSectionOpType.getName()).thenReturn("Nonsection");
        when(nonSectionOpType.canPrelabel()).thenReturn(true);
        when(nonSectionOpType.canCreateSection()).thenReturn(false);
        OperationType otherOpType = EntityFactory.makeOperationType("Other", null);

        return Stream.of(
                Arguments.of(List.of(), List.of(), null, null, sectionOpType, null),
                Arguments.of(block.getBarcode(), A1, blockSampleId, block, sectionOpType, null),
                Arguments.of(block.getBarcode(), null, blockSampleId, block, sectionOpType, null),
                Arguments.of(nonBlock.getBarcode(), null, sectionSampleId, nonBlock, otherOpType, null),

                Arguments.of(destroyedLw.getBarcode(), A1, sectionSampleId, destroyedLw, otherOpType, "Labware already destroyed: ["+destroyedLw.getBarcode()+"]"),
                Arguments.of(releasedLw.getBarcode(), A1, sectionSampleId, releasedLw, otherOpType, "Labware already released: ["+releasedLw.getBarcode()+"]"),
                Arguments.of(discardedLw.getBarcode(), A1, sectionSampleId, discardedLw, otherOpType, "Labware already discarded: ["+discardedLw.getBarcode()+"]"),
                Arguments.of(null, A1, blockSampleId, null, sectionOpType, "Missing source barcode."),
                Arguments.of("", A1, blockSampleId, null, sectionOpType, "Missing source barcode."),
                Arguments.of("404", A1, blockSampleId, null, sectionOpType, "Unknown labware barcode: [404]"),
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

    /** @see #testCheckActions */
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
                //Duplicate actions
                Arguments.of("STAN-100", List.of(new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(FIRST, 4, src, null),
                        new PlanRequestAction(SECOND, 4, srcAlt, null)),
                        lt, "Actions for labware STAN-100 contain duplicate action: (address=A1, sampleId=4, source={STAN-000, A1})"),
                //Duplicate actions from a non-block source without a barcode
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
        LabelType adhLabel = new LabelType(100, "adh");
        LabwareType adhLt = new LabwareType(200, "Visium ADH", 4, 2, adhLabel, false);
        List<LabwareType> lts = List.of(lt, preLt, adhLt);
        List<PlanRequestAction> noActions = List.of();

        return Stream.of(
                Arguments.of(List.of(new PlanRequestLabware(ltName, null, noActions),
                        new PlanRequestLabware(ltName, null, noActions),
                        new PlanRequestLabware(preName, "SPECIALBARCODE", noActions)),
                        lts, null, null),
                Arguments.of(List.of(new PlanRequestLabware(adhLt.getName(), null, noActions),
                        new PlanRequestLabware(ltName, null, noActions)),
                        lts, null, true),
                Arguments.of(List.of(new PlanRequestLabware(adhLt.getName(), null, noActions),
                        new PlanRequestLabware(ltName, null, noActions)), lts,
                        "Labware of type Visium ADH must have one tissue per row.",
                        false),

                Arguments.of(List.of(), null, "No labware are specified in the plan request.", null),
                Arguments.of(List.of(new PlanRequestLabware(preName, "SPECIAL1", noActions),
                        new PlanRequestLabware(preName, "SPECIAL1", noActions)),
                        lts, "Repeated barcode given for new labware: SPECIAL1", null),
                Arguments.of(new PlanRequestLabware(null, null, noActions),
                        lts, "Missing labware type.", null),
                Arguments.of(new PlanRequestLabware(preName, "EXTANT", noActions),
                        lts, "Labware with the barcode EXTANT already exists in the database.", null),
                Arguments.of(new PlanRequestLabware(preName, null, noActions),
                        lts, "No barcode supplied for new labware of type "+preName+".", null),
                Arguments.of(new PlanRequestLabware(ltName, "SPECIAL1", noActions),
                        lts, "Unexpected barcode supplied for new labware of type "+ltName+".", null),
                Arguments.of(new PlanRequestLabware("Mist", null, noActions),
                        lts, "Unknown labware type: [MIST]", null),
                Arguments.of(List.of(
                        new PlanRequestLabware("Mist", null, noActions),
                        new PlanRequestLabware("MIST", null, noActions),
                        new PlanRequestLabware("Fog", null, noActions)
                ), lts, "Unknown labware types: [MIST, FOG]", null),
                Arguments.of(new PlanRequestLabware(preName, "SPECIAL*", noActions),
                        lts, "Invalid barcode: SPECIAL*", null)

        );
    }

    static Stream<Arguments> hasDividedLayoutData() {
        // lwmap, prl, lt, expected

        Tissue[] tissues = IntStream.range(0,3)
                .mapToObj(i -> EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation()))
                .toArray(Tissue[]::new);
        Sample[] sams = IntStream.range(0,3)
                .mapToObj(i -> new Sample(10+i, null, tissues[i], EntityFactory.getBioState()))
                .toArray(Sample[]::new);
        Labware[] blocks = Arrays.stream(sams)
                .map(EntityFactory::makeBlock)
                .toArray(Labware[]::new);
        Labware someOtherBlock = EntityFactory.makeBlock(sams[0]);

        UCMap<Labware> sourceLwMap = UCMap.from(Labware::getBarcode, blocks);

        LabwareType lt = new LabwareType(10, "Visium ADH", 4, 2, new LabelType(6, "adh"), false);

        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address B1 = new Address(2,1);
        final Address C1 = new Address(3,1);
        final Address D2 = new Address(4,2);
        final Address E1 = new Address(5,1); // address out of range
        final Stream<Arguments> argStream = Arrays.stream(new Object[][]{
                {A1, blocks[0], A1, blocks[0], A1, blocks[0], A2, blocks[0], B1, blocks[0], C1, blocks[1], C1, blocks[1], D2, blocks[1], true},
                {A2, blocks[0], C1, blocks[0], true},
                {A1, blocks[0], A1, blocks[0], A2, blocks[0], true},
                {C1, blocks[0], true},
                {A1, blocks[0], C1, blocks[1], E1, blocks[2], true}, // E1 entry is ignored
                {null, blocks[0], true}, // missing address is ignored
                {A1, null, true}, // missing source is ignored
                {A1, someOtherBlock, true}, // labware not found is ignored
                {true},
                {A1, blocks[0], A2, blocks[0], A2, blocks[1], C1, blocks[2], false},
                {A1, blocks[0], C1, blocks[1], D2, blocks[2], false},
        }).map(arr -> Arguments.of(sourceLwMap, toPRL(lt.getName(), arr), lt, 2, arr[arr.length - 1]));

        final Stream<Arguments> otherArgStream = Stream.of(
                Arguments.of(sourceLwMap, new PlanRequestLabware(lt.getName(), null,
                        List.of(
                                new PlanRequestAction(A1, sams[0].getId(), new PlanRequestSource(blocks[0].getBarcode(), A1), null),
                                new PlanRequestAction(A1, sams[1].getId(), new PlanRequestSource(blocks[0].getBarcode(), A2), null))),
                        lt, 2, true), // Invalid source address is ignored
                Arguments.of(sourceLwMap, new PlanRequestLabware(lt.getName(), null,
                        List.of(new PlanRequestAction(A1, sams[0].getId(), new PlanRequestSource(blocks[0].getBarcode(), A1), null),
                                new PlanRequestAction(A2, sams[1].getId(), new PlanRequestSource(blocks[0].getBarcode(), A1), null))),
                        lt, 2, true) // Sample id not present in slot is ignored
        );

        return Stream.concat(argStream, otherArgStream);

    }

    private static PlanRequestLabware toPRL(String ltName, Object[] data) {
        final Address A1 = new Address(1,1);

        List<PlanRequestAction> pras = IntStream.range(0, (data.length-1)/2)
                .mapToObj(i -> {
                    Address ad = (Address) data[2*i];
                    Labware lw = (Labware) data[2*i+1];
                    if (lw==null) {
                        return new PlanRequestAction(ad, 5, null, null);
                    }
                    return new PlanRequestAction(ad, lw.getFirstSlot().getSamples().get(0).getId(),
                            new PlanRequestSource(lw.getBarcode(), i%2==0 ? A1 : null), null);
                })
                .collect(toList());
        return new PlanRequestLabware(ltName, null, pras);
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

    private static <E> Stream<E> toStream(Object obj) {
        return EntityFactory.<E>nullableObjToList(obj).stream();
    }

    private static <E> Iterator<E> toIterator(Object obj) {
        return EntityFactory.<E>nullableObjToList(obj).iterator();
    }
}
