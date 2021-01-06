package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * @author dr6
 */
@Entity
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String text;
    private String category;
    private boolean enabled = true;

    public Comment() {}

    public Comment(Integer id, String text, String category) {
        this(id, text, category, true);
    }

    public Comment(Integer id, String text, String category, boolean enabled) {
        this.id = id;
        this.text = text;
        this.category = category;
        this.enabled = enabled;
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

    public String getCategory() {
        return this.category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comment that = (Comment) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.text, that.text)
                && Objects.equals(this.category, that.category)
                && this.enabled==that.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, category);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("text", text)
                .add("category", category)
                .add("enabled", enabled)
                .toString();
    }
}
