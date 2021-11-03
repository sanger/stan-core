package uk.ac.sanger.sccp.stan.request;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Part of a request to record a measurement (as part of an operation)
 * @author dr6
 */
public class StringMeasurement {
    private String name;
    private String value;

    public StringMeasurement() {}

    public StringMeasurement(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringMeasurement that = (StringMeasurement) o;
        return (Objects.equals(this.name, that.name)
                && Objects.equals(this.value, that.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        return String.format("(%s: %s)", this.name, repr(this.value));
    }
}
