package uk.ac.sanger.sccp.stan.service.measurements;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.SlotMeasurementRequest;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SlotMeasurementValidatorImp}
 */
public class TestSlotMeasurementValidator {
    private static Sanitiser<String> simpleSanitiser;
    @BeforeAll
    static void setup() {
        simpleSanitiser = (problems, value) -> {
            if (value==null || value.isEmpty()) {
                problems.add("No value");
                return null;
            }
            if (value.indexOf('!') >= 0) {
                problems.add("Bad measurement value: "+value);
                return null;
            }
            return value.toLowerCase();
        };
    }

    private static Labware makeLw() {
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        Sample sam = EntityFactory.getSample();
        return EntityFactory.makeLabware(lt, sam, sam);
    }

    @Test
    public void testValidateSlotMeasurements_invalid() {
        SlotMeasurementValidatorImp val = spy(new SlotMeasurementValidatorImp(List.of("Alpha", "Beta")));
        val.setValueSanitiser("Alpha", simpleSanitiser);
        Labware lw = makeLw();
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address B1 = new Address(2,1);
        final Address B2 = new Address(2,2);
        final Address C1 = new Address(3,1);
        List<SlotMeasurementRequest> sms1 = List.of(
                new SlotMeasurementRequest(A1, "alpha", "v1"),
                new SlotMeasurementRequest(A1, "beta", "v2!"),
                new SlotMeasurementRequest(A2, "alpha", "v3"),
                new SlotMeasurementRequest(A2, "ALPHA", "v33"),
                new SlotMeasurementRequest(A2, "BETA", "v34"),
                new SlotMeasurementRequest(A2, "beta", "v34"),
                new SlotMeasurementRequest(A2, "Gamma", "v4"),
                new SlotMeasurementRequest(B1, "ALPHA", "v5"),
                new SlotMeasurementRequest(C1, "Alpha", "V6"),
                new SlotMeasurementRequest(null, "Alpha", "v1"),
                new SlotMeasurementRequest(B2, null, "v1"),
                new SlotMeasurementRequest(B2, "BETA", null),
                new SlotMeasurementRequest(B2, "Alpha", "Yikes!")
        );

        List<SlotMeasurementRequest> sms2 = List.of(
                new SlotMeasurementRequest(A1, "Delta", "Zoom!")
        );

        val.validateSlotMeasurements(lw, sms1);
        val.validateSlotMeasurements(null, sms2);

        verify(val, times(sms1.size()+sms2.size())).sanitiseSlotMeasurement(any(), any(), any(), any());
        for (SlotMeasurementRequest sm : sms1) {
            verify(val).sanitiseSlotMeasurement(same(lw), same(sm), anySet(), anySet());
        }
        for (SlotMeasurementRequest sm : sms2) {
            verify(val).sanitiseSlotMeasurement(isNull(), same(sm), anySet(), anySet());
        }

        Set<String> problems = val.compileProblems();
        Set<String> expectedProblems = Set.of(
                "Measurements given without a name.",
                "Measurements given without a value.",
                "Measurements given without a slot address.",
                "Unexpected measurements specified for this operation: [Gamma, Delta]",
                "Bad measurement value: Yikes!",
                "Invalid slot specified in measurement for labware "+lw.getBarcode()+": [C1]",
                "Empty slots specified in measurements for labware "+lw.getBarcode()+": [B1, B2]",
                "Same measurements specified multiple times in the same slots of labware "+lw.getBarcode()+": Alpha in A2; Beta in A2"
        );
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @Test
    public void testValidateSlotMeasurements_valid() {
        SlotMeasurementValidatorImp val = spy(new SlotMeasurementValidatorImp(List.of("Alpha", "Beta")));
        val.setValueSanitiser("Alpha", simpleSanitiser);
        Labware lw = makeLw();
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        List<SlotMeasurementRequest> sms = List.of(
                new SlotMeasurementRequest(A1, "alpha", "j1"),
                new SlotMeasurementRequest(A1, "BETA", "J2!"),
                new SlotMeasurementRequest(A2, "Alpha", "J3")
        );
        List<SlotMeasurementRequest> san = val.validateSlotMeasurements(lw, sms);


        verify(val, times(sms.size())).sanitiseSlotMeasurement(any(), any(), any(), any());
        for (SlotMeasurementRequest sm : sms) {
            verify(val).sanitiseSlotMeasurement(same(lw), same(sm), anySet(), anySet());
        }
        assertThat(san).containsExactly(
                new SlotMeasurementRequest(A1, "Alpha", "j1"),
                new SlotMeasurementRequest(A1, "Beta", "J2!"),
                new SlotMeasurementRequest(A2, "Alpha", "j3")
        );

        assertThat(val.compileProblems()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("sanitiseSlotMeasurementsArgs")
    public void testSanitiseSlotMeasurement(Labware lw, SlotMeasurementRequest sm, SlotMeasurementRequest expected,
                                            boolean addressInvalid, boolean addressEmpty,
                                            Collection<String> expectedProblems) {
        Set<Address> invalidAddresses = new HashSet<>(1);
        Set<Address> emptyAddresses = new HashSet<>(1);
        SlotMeasurementValidatorImp val = new SlotMeasurementValidatorImp(List.of("Alpha", "Beta"));
        val.setValueSanitiser("Alpha", simpleSanitiser);

        assertEquals(expected, val.sanitiseSlotMeasurement(lw, sm, invalidAddresses, emptyAddresses));
        if (addressInvalid) {
            assertThat(invalidAddresses).containsExactly(sm.getAddress());
        } else {
            assertThat(invalidAddresses).isEmpty();
        }
        if (addressEmpty) {
            assertThat(emptyAddresses).containsExactly(sm.getAddress());
        } else {
            assertThat(emptyAddresses).isEmpty();
        }

        assertThat(val.compileProblems()).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> sanitiseSlotMeasurementsArgs() {
        Labware lw = makeLw();
        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        final Address C1 = new Address(3,1);
        return Arrays.stream(new Object[][] {
                {lw, new SlotMeasurementRequest(A1, "alpha", "VALUE"),
                 new SlotMeasurementRequest(A1, "Alpha", "value")},
                {lw, new SlotMeasurementRequest(A1, "Beta", "Value!"),
                 new SlotMeasurementRequest(A1, "Beta", "Value!")},
                {null, new SlotMeasurementRequest(C1, "Alpha", "value"), null},
                {lw, new SlotMeasurementRequest(A1, null, "value"), null,
                        "Measurements given without a name."},
                {lw, new SlotMeasurementRequest(A1, "Alpha", null), null,
                        "Measurements given without a value."},
                {lw, new SlotMeasurementRequest(null, "Alpha", "value"), null,
                        "Measurements given without a slot address."},
                {lw, new SlotMeasurementRequest(null, "", ""), null,
                        "Measurements given without a name.",
                        "Measurements given without a value.",
                        "Measurements given without a slot address."},
                {lw, new SlotMeasurementRequest(A1, "Gamma", "value"), null,
                        "Unexpected measurement specified for this operation: [Gamma]"},
                {lw, new SlotMeasurementRequest(B1, "Alpha", "value"), null},
                {lw, new SlotMeasurementRequest(C1, "Alpha", "value"), null},
                {lw, new SlotMeasurementRequest(A1, "Alpha", "value!"), null,
                        "Bad measurement value: value!"},
        }).map(arr -> {
            Set<String> expectedProblems = new HashSet<>(arr.length-3);
            for (int i = 3; i < arr.length; ++i) {
                expectedProblems.add((String) arr[i]);
            }
            boolean slotInvalid = false;
            boolean slotEmpty = false;
            if (arr[0] instanceof Labware) {
                Address ad = ((SlotMeasurementRequest) arr[1]).getAddress();
                if (ad!=null) {
                    var optSlot = ((Labware) arr[0]).optSlot(ad);
                    if (optSlot.isEmpty()) {
                        slotInvalid = true;
                    } else if (optSlot.get().getSamples().isEmpty()) {
                        slotEmpty = true;
                    }
                }
            }
            return Arguments.of(arr[0], arr[1], arr[2], slotInvalid, slotEmpty, expectedProblems);
        });
    }

    @Test
    public void testIsSlotPopulated() {
        Labware lw = makeLw();
        SlotMeasurementValidatorImp val = new SlotMeasurementValidatorImp(List.of());
        Stream.of(new Address(1,1), new Address(1,2))
                        .forEach(address -> assertTrue(val.isSlotPopulated(lw, address)));
        assertFalse(val.isSlotPopulated(lw, new Address(2,1)));
        assertNull(val.isSlotPopulated(lw, new Address(3,1)));
        assertNull(val.isSlotPopulated(lw, null));
    }

    @Test
    public void testSanitiseName() {
        SlotMeasurementValidatorImp val = new SlotMeasurementValidatorImp(List.of("Alpha", "Beta"));
        String[] data = {
                "Alpha", "Alpha", "alpha", "Alpha", "ALPHA", "Alpha",
                "Beta", "Beta", "BETA", "Beta",
                "Gamma", null, "", null, null, null
        };
        for (int i = 0; i < data.length; i+=2) {
            assertEquals(data[i+1], val.sanitiseName(data[i]));
        }
    }

    @Test
    public void testFactory() {
        assertThat(new SlotMeasurementValidatorFactory().getSlotMeasurementValidator(List.of()))
                .isInstanceOf(SlotMeasurementValidatorImp.class);
    }
}