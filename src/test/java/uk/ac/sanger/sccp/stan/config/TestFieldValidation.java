package uk.ac.sanger.sccp.stan.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFieldValidation {
    FieldValidation fieldValidation;

    @BeforeEach
    void setup() {
        fieldValidation = new FieldValidation();
    }

    @Test
    public void testReplicateNumber() {
        Validator<String> val = fieldValidation.replicateValidator();
        final Consumer<String> problemConsumer = msg -> {};
        String[] valid = {"1", "A", "1A", "A1", "11aabb33", "1-4-A", "B.5.C", "X_X_7_7", "1-B.C_E"};
        for (String string : valid) {
            assertTrue(val.validate(string, problemConsumer), string);
        }
        String[] invalid = {"", "*", "A*", " 1", "A ", "-1", "A.", "1A..N", "A-_4"};
        for (String string : invalid) {
            assertFalse(val.validate(string, problemConsumer), string);
        }
    }

}