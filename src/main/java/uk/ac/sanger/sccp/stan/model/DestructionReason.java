package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

/**
 * A reason for destroying labware
 * @author dr6
 */
@Entity
public class DestructionReason implements HasEnabled {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String text;
    private boolean enabled = true;

    public DestructionReason() {}

    public DestructionReason(Integer id, String text) {
        this.id = id;
        this.text = text;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DestructionReason that = (DestructionReason) o;
        return (this.enabled == that.enabled
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.text, that.text));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : text!=null ? text.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getText();
    }
}
