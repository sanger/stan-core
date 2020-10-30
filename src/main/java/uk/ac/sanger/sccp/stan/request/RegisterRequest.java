package uk.ac.sanger.sccp.stan.request;

import com.google.common.base.MoreObjects;

import java.util.List;
import java.util.Objects;

/**
 * The information required to register some blocks.
 * @author dr6
 */
public class RegisterRequest {
    private List<BlockRegisterRequest> blocks;

    public List<BlockRegisterRequest> getBlocks() {
        return this.blocks;
    }

    public void setBlocks(List<BlockRegisterRequest> blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterRequest that = (RegisterRequest) o;
        return (Objects.equals(this.blocks, that.blocks));
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocks);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("blocks", blocks)
                .toString();
    }
}
