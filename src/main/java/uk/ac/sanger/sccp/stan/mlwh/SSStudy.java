package uk.ac.sanger.sccp.stan.mlwh;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A study in SequenceScape
 * @author dr6
 */
public class SSStudy {
    private final int id;
    private final String name;

    public SSStudy(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SSStudy that = (SSStudy) o;
        return (this.id == that.id
                && Objects.equals(this.name, that.name));
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.id);
    }

    @Override
    public String toString() {
        return String.format("SSStudy(%s, %s)", this.id, repr(this.name));
    }
}
