package uk.ac.sanger.sccp.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link ObjectDescriber}
 * @author dr6
 */
public class TestObjectDescriber {
    @Test
    public void testDefault() {
        final String string = BasicUtils.describe("Foo")
                .add("Alpha", null)
                .add("Beta", "X")
                .add("Gamma", "\"quoted\"")
                .add("Delta", 3)
                .toString();
        assertEquals("Foo{Alpha=null, Beta=X, Gamma=\"quoted\", Delta=3}", string);
    }

    @Test
    public void testOmitNullValuesAndRepr() {
        final String string = BasicUtils.describe("Foo")
                .add("Alpha", null)
                .add("Beta", "X")
                .add("Gamma", "\"quoted\"")
                .add("Delta", 3)
                .omitNullValues()
                .reprStringValues()
                .toString();
        assertEquals("Foo{Beta=\"X\", Gamma=\"\\\"quoted\\\"\", Delta=3}", string);
    }
}
