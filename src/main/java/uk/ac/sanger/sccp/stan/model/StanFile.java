package uk.ac.sanger.sccp.stan.model;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A stored file
 * @author dr6
 */
@Entity
public class StanFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Generated(GenerationTime.INSERT)
    private LocalDateTime created;
    @ManyToOne
    private Work work;

    private String name;
    private String path;
    private LocalDateTime deprecated;


    public StanFile() {}

    public StanFile(Integer id, LocalDateTime created, Work work, String name, String path, LocalDateTime deprecated) {
        this.id = id;
        this.created = created;
        this.work = work;
        this.name = name;
        this.path = path;
        this.deprecated = deprecated;
    }

    public StanFile(Work work, String name, String path) {
        this(null, null, work, name, path, null);
    }

    /** The primary key of this entry in the database */
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /** The timestamp that this entry was created */
    public LocalDateTime getCreated() {
        return this.created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    /** The timestamp (if any) that this file was replaced or deprecated */
    public LocalDateTime getDeprecated() {
        return this.deprecated;
    }

    public void setDeprecated(LocalDateTime deprecated) {
        this.deprecated = deprecated;
    }

    /** The user-facing name of this file */
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The path where this file is stored inside the volume */
    public String getPath() {
        return this.path;
    }

    public void setPath(String filePath) {
        this.path = filePath;
    }

    /** Is this file active? (Not deprecated) */
    public boolean isActive() {
        return (this.deprecated==null);
    }

    /** The work this file is associated with */
    public Work getWork() {
        return this.work;
    }

    public void setWork(Work work) {
        this.work = work;
    }

    /** The url where this file may be downloaded */
    public String getUrl() {
        return "files/"+this.getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StanFile that = (StanFile) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.created, that.created)
                && Objects.equals(this.deprecated, that.deprecated)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.path, that.path)
                && Objects.equals(this.work, that.work));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, created, deprecated, name, path, work);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("created", created)
                .add("work", work)
                .addRepr("name", name)
                .addRepr("path", path)
                .addIfNotNull("deprecated", deprecated)
                .toString();
    }
}
