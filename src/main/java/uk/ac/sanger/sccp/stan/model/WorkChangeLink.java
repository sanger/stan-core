package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * A link between work and operation made or removed in a work change.
 * @author dr6
 */
@Entity
public class WorkChangeLink implements HasIntId {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer workChangeId;
    private Integer operationId;
    private Integer workId;
    private boolean link;

    public WorkChangeLink() {
    }

    public WorkChangeLink(Integer id, Integer workChangeId, Integer operationId, Integer workId, boolean link) {
        this.id = id;
        this.workChangeId = workChangeId;
        this.operationId = operationId;
        this.workId = workId;
        this.link = link;
    }

    public WorkChangeLink(Integer workChangeId, Integer operationId, Integer workId, boolean link) {
        this(null, workChangeId, operationId, workId, link);
    }

    @Override
    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getWorkChangeId() {
        return this.workChangeId;
    }

    public void setWorkChangeId(Integer workChangeId) {
        this.workChangeId = workChangeId;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    public Integer getWorkId() {
        return this.workId;
    }

    public void setWorkId(Integer workId) {
        this.workId = workId;
    }

    /** True if a link was added; false if a link was removed */
    public boolean isLink() {
        return this.link;
    }

    public void setLink(boolean link) {
        this.link = link;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkChangeLink that = (WorkChangeLink) o;
        return (this.link == that.link
                && Objects.equals(this.id, that.id)
                && Objects.equals(this.workChangeId, that.workChangeId)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.workId, that.workId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, workChangeId, operationId, workId, link);
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("workChangeId", workChangeId)
                .add("operationId", operationId)
                .add("workId", workId)
                .add("link", link)
                .toString();
    }
}
