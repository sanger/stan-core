package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests {@link LabwareValidator}
 * @author dr6
 */
public class TestLabwareValidator {
    private LabwareValidator validator;

    @BeforeEach
    void setup() {
        validator = spy(LabwareValidator.class);
    }

    @Test
    public void testLabwareValidatorFactory() {
        LabwareValidatorFactory factory = new LabwareValidatorFactory();
        assertThat(factory.getValidator().getLabware()).isNullOrEmpty();
        List<Labware> labware = List.of(EntityFactory.getTube());
        assertThat(factory.getValidator(labware).getLabware()).hasSameElementsAs(labware);
    }

    @Test
    public void testErrors() {
        final Function<String, IllegalArgumentException> errorFunction = IllegalArgumentException::new;
        validator.throwError(errorFunction);

        validator.addError("Error one.");
        validator.addError("Error two: %s.", "Bananas");
        assertThat(assertThrows(IllegalArgumentException.class, () -> validator.throwError(errorFunction)))
                .hasMessage("Error one. Error two: Bananas.");
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testLoadLabware(boolean anyMissing) {
        LabwareRepo mockLwRepo = mock(LabwareRepo.class);
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        List<Labware> labware = List.of(lw1, lw2);
        List<String> barcodes;
        if (anyMissing) {
            barcodes = Arrays.asList("X-001", "X-002", null, lw1.getBarcode(), lw2.getBarcode());
        } else {
            barcodes = List.of(lw1.getBarcode(), lw2.getBarcode());
        }
        when(mockLwRepo.findByBarcodeIn(any())).thenReturn(labware);

        assertThat(validator.loadLabware(mockLwRepo, barcodes)).hasSameElementsAs(labware);

        verify(mockLwRepo).findByBarcodeIn(barcodes);

        if (anyMissing) {
            assertThat(validator.getErrors()).containsOnly("Invalid labware barcodes: [\"X-001\", \"X-002\", null].");
        } else {
            assertThat(validator.getErrors()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3,4,8,9,15})
    public void testValidateSources(int requirements) {
        boolean unique = (requirements&1)!=0;
        boolean single = (requirements&2)!=0;
        boolean singleSlot = (requirements&4)!=0;
        boolean block = (requirements&8)!=0;

        doNothing().when(validator).validateUnique();
        doNothing().when(validator).validateNonEmpty();
        doNothing().when(validator).validateSingleSample();
        doNothing().when(validator).validateStates();
        doNothing().when(validator).validateOneFilledSlot();
        doNothing().when(validator).validateBlock();

        validator.setUniqueRequired(unique);
        validator.setSingleSample(single);
        validator.setOneFilledSlotRequired(singleSlot);
        validator.setBlockRequired(block);
        validator.validateSources();

        verify(validator, times(unique ? 1 : 0)).validateUnique();
        verify(validator).validateNonEmpty();
        verify(validator, times(single ? 1 : 0)).validateSingleSample();
        verify(validator, times(singleSlot && !single ? 1 : 0)).validateOneFilledSlot();
        verify(validator, times(block ? 1 : 0)).validateBlock();
        verify(validator).validateStates();
    }

    @ParameterizedTest
    @CsvSource(value = {"right, right, right;",
            "wrong, right, right; Labware contains samples not in bio state Right: [(STAN-A1, wrong)].",
            "wrong, Right, bad; Labware contains samples not in bio state Right: [(STAN-A1, wrong), (STAN-A3, bad)]."
    },delimiterString = ";")
    public void testValidateBioState(String statesJoined, String expectedError) {
        UCMap<BioState> namedBioStates = new UCMap<>();
        BioState rightState = new BioState(1, "Right");
        namedBioStates.put("Right", rightState);
        final String[] stateNames = statesJoined.split(",\\s*");
        Tissue tissue = EntityFactory.getTissue();
        final Sample[] samples = new Sample[stateNames.length];
        for (int i = 0; i < stateNames.length; i++) {
            String stateName = stateNames[i];
            BioState bs = namedBioStates.get(stateName);
            if (bs == null) {
                bs = new BioState(i+2, stateName);
                namedBioStates.put(stateName, bs);
            }
            samples[i] = new Sample(i+10, null, tissue, bs);
        }
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = Arrays.stream(samples)
                .map(sam -> EntityFactory.makeLabware(lt, sam))
                .collect(toList());
        for (int i = 0; i < labware.size(); ++i) {
            labware.get(i).setBarcode("STAN-A"+(i+1));
        }

        validator.setLabware(labware);
        validator.validateBioState(rightState);
        if (expectedError==null) {
            assertThat(validator.getErrors()).isEmpty();
        } else {
            assertThat(validator.getErrors()).containsExactly(expectedError);
        }
    }

    @Test
    public void testValidateUnique() {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());

        Runnable action = validator::validateUnique;
        testValidator(List.of(), action);
        testValidator(List.of(lw1), action);
        testValidator(List.of(lw1, lw2), action);
        testValidator(List.of(lw1, lw2, lw1), action, errorBarcode("Labware is repeated", lw1));
    }

    @ParameterizedTest
    @MethodSource("validateUniqueFromBarcodesData")
    public void testValidateUniqueFromBarcodes(List<String> barcodes, List<Labware> labware, List<String> expectedProblems) {
        LabwareRepo mockLwRepo = mock(LabwareRepo.class);
        when(mockLwRepo.findByBarcodeIn(barcodes)).thenReturn(labware);
        validator.loadLabware(mockLwRepo, barcodes);
        validator.validateUnique();
        assertThat(validator.getErrors()).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateUniqueFromBarcodesData() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        lw1.setBarcode("STAN-1");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.setBarcode("STAN-2");
        Labware lw3 = EntityFactory.makeEmptyLabware(lt);
        lw3.setBarcode("STAN-3");

        return Arrays.stream(new Object[][] {
                {List.of("STAN-1"), List.of(lw1), List.of()},
                {List.of("STAN-1", "STAN-2", "STAN-3"), List.of(lw1, lw2, lw3), List.of()},
                {List.of("STAN-1", "STAN-2", "STAN-3", "stan-2", "stan-1"), List.of(lw1, lw2, lw3),
                        List.of("Labware is repeated: [STAN-1, STAN-2].")}
        }).map(Arguments::of);
    }

    @Test
    public void testValidateNonEmpty() {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware emptyLabware = EntityFactory.makeEmptyLabware(lt);
        Labware nonemptyLabware = EntityFactory.makeLabware(lt, sample);

        Runnable action = validator::validateNonEmpty;
        testValidator(List.of(), action);
        testValidator(List.of(nonemptyLabware), action);
        testValidator(List.of(nonemptyLabware, emptyLabware), action,errorBarcode("Labware is empty", emptyLabware));
    }

    @Test
    public void testValidateStates_noLabware() {
        validator.setLabware(List.of());
        validator.validateStates();
        verify(validator, never()).validateState(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testValidateStates(boolean usedAllowed) {
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> lw = IntStream.range(0, 5).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).collect(toList());
        lw.get(1).setDiscarded(true);
        lw.get(2).setDestroyed(true);
        lw.get(3).setReleased(true);
        lw.get(4).setUsed(true);
        validator.setLabware(lw);
        validator.setUsedAllowed(usedAllowed);
        validator.validateStates();

        // cannot verify the identity of method references
        verify(validator).validateState(any(), eq("discarded"));
        verify(validator).validateState(any(), eq("destroyed"));
        verify(validator).validateState(any(), eq("released"));
        verify(validator, times(usedAllowed ? 0 : 1)).validateState(any(), eq("used"));
        // ... so we'll verify the effects instead
        assertThat(validator.getErrors()).hasSize(usedAllowed ? 3 : 4);
        assertThat(validator.getErrors()).contains(errorBarcode("Labware is discarded", lw.get(1)));
        assertThat(validator.getErrors()).contains(errorBarcode("Labware is destroyed", lw.get(2)));
        assertThat(validator.getErrors()).contains(errorBarcode("Labware is released", lw.get(3)));
        if (!usedAllowed) {
            assertThat(validator.getErrors()).contains(errorBarcode("Labware is used", lw.get(4)));
        }
    }

    @Test
    public void testValidateState() {
        final Labware goodLw = EntityFactory.getTube();
        final Labware badLw = EntityFactory.makeEmptyLabware(goodLw.getLabwareType());
        final Predicate<Labware> bad = (lw -> lw==badLw);

        final Runnable action = () -> validator.validateState(bad, "bad");

        testValidator(List.of(), action);
        testValidator(List.of(goodLw), action);
        testValidator(List.of(goodLw, badLw), action, errorBarcode("Labware is bad", badLw));
    }

    @Test
    public void testValidateSingleSample() {
        final Runnable action = validator::validateSingleSample;
        testValidator(List.of(), action);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId()+1, 800, sample1.getTissue(), sample1.getBioState());
        Labware goodLw = EntityFactory.makeLabware(lt, sample1);
        Labware multiSlotLw = EntityFactory.makeLabware(lt, sample1, sample1);
        Labware multiSampleLw = EntityFactory.makeEmptyLabware(lt);
        multiSampleLw.getFirstSlot().getSamples().addAll(List.of(sample1, sample2));

        testValidator(List.of(goodLw), action);

        testValidator(List.of(goodLw, multiSampleLw, multiSlotLw, multiSlotLw), action,
                errorBarcode("Labware contains multiple samples", multiSampleLw),
                errorBarcode("Labware contains samples in multiple slots", multiSlotLw));
    }

    @Test
    public void testValidateBlock() {
        final Runnable action = validator::validateBlock;
        testValidator(List.of(), action);
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, 800, sam1.getTissue(), sam1.getBioState());
        Labware goodLw = EntityFactory.makeBlock(sam1);
        Labware badLw = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam2);

        testValidator(List.of(goodLw), action);

        testValidator(List.of(goodLw, badLw), action,
                errorBarcode("Labware contains non-block samples", badLw));
    }

    @Test
    public void testValidateOneFilledSlot() {
        final Runnable action = validator::validateOneFilledSlot;
        testValidator(List.of(), action);
        LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId()+1, 800, sample1.getTissue(), sample1.getBioState());
        Labware goodLw = EntityFactory.makeLabware(lt, sample1);
        goodLw.getFirstSlot().addSample(sample2); // Two samples in one slot
        Labware badLw1 = EntityFactory.makeLabware(lt, sample1, sample1); // One sample in two slots
        Labware badLw2 = EntityFactory.makeLabware(lt, sample1, sample2); // Two samples across two slots
        testValidator(List.of(goodLw), action);
        testValidator(List.of(goodLw, badLw1, badLw2, badLw1), action,
                errorBarcode("Labware contains samples in multiple slots", badLw1, badLw2));
    }

    private void testValidator(Collection<Labware> labware, Runnable action, String... expectedErrors) {
        validator.getErrors().clear();
        validator.setLabware(labware);
        action.run();
        assertThat(validator.getErrors()).containsOnly(expectedErrors);
    }

    private String errorBarcode(String message, Object... bcs) {
        StringBuilder sb = new StringBuilder(message).append(": [");
        boolean first = true;
        for (Object bc : bcs) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            if (bc instanceof Labware) {
                sb.append(((Labware) bc).getBarcode());
            } else {
                sb.append(bc);
            }
        }
        return sb.append("].").toString();
    }
}
