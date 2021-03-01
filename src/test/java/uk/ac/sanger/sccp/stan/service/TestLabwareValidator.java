package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

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
    @ValueSource(ints={0,1,2,3})
    public void testValidateSources(int requirements) {
        boolean unique = (requirements&1)!=0;
        boolean single = (requirements&2)!=0;

        doNothing().when(validator).validateUnique();
        doNothing().when(validator).validateNonEmpty();
        doNothing().when(validator).validateSingleSample();
        doNothing().when(validator).validateStates();

        validator.setUniqueRequired(unique);
        validator.setSingleSample(single);
        validator.validateSources();

        verify(validator, times(unique ? 1 : 0)).validateUnique();
        verify(validator).validateNonEmpty();
        verify(validator, times(single ? 1 : 0)).validateSingleSample();
        verify(validator).validateStates();
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

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testValidateStates(boolean anyLabware) {
        List<Labware> lw;
        if (anyLabware) {
            LabwareType lt = EntityFactory.getTubeType();
            lw = IntStream.range(0, 4).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).collect(toList());
            lw.get(1).setDiscarded(true);
            lw.get(2).setDestroyed(true);
            lw.get(3).setReleased(true);
        } else {
            lw = List.of();
        }
        validator.setLabware(lw);
        validator.validateStates();
        if (!anyLabware) {
            verify(validator, never()).validateState(any(), anyString());
            return;
        }

        // cannot verify the identity of method references
        verify(validator).validateState(any(), eq("discarded"));
        verify(validator).validateState(any(), eq("destroyed"));
        verify(validator).validateState(any(), eq("released"));
        // ... so we'll verify the effects instead
        assertThat(validator.getErrors()).containsOnly(
                errorBarcode("Labware is discarded", lw.get(1)),
                errorBarcode("Labware is destroyed", lw.get(2)),
                errorBarcode("Labware is released", lw.get(3))
        );
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
