package uk.ac.sanger.sccp.stan.request.register;

import java.util.List;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * The information required to register some blocks.
 * @author dr6
 */
public class RegisterRequest {
    private List<BlockRegisterRequest_old> blocks;
    private List<String> workNumbers;

    public RegisterRequest() {
        this(null, null);
    }

    public RegisterRequest(List<BlockRegisterRequest_old> blocks) {
        this(blocks, null);
    }

    public RegisterRequest(List<BlockRegisterRequest_old> blocks, List<String> workNumbers) {
        setBlocks(blocks);
        setWorkNumbers(workNumbers);
    }

    public List<BlockRegisterRequest_old> getBlocks() {
        return this.blocks;
    }

    public void setBlocks(List<BlockRegisterRequest_old> blocks) {
        this.blocks = (blocks==null ? List.of() : blocks);
    }

    public List<String> getWorkNumbers() {
        return this.workNumbers;
    }

    public void setWorkNumbers(List<String> workNumbers) {
        this.workNumbers = (workNumbers==null ? List.of() : workNumbers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegisterRequest that = (RegisterRequest) o;
        return (this.blocks.equals(that.blocks) && this.workNumbers.equals(that.workNumbers));
    }

    @Override
    public int hashCode() {
        return 31*this.blocks.hashCode() + this.workNumbers.hashCode();
    }

    @Override
    public String toString() {
        return describe(this)
                .add("blocks", blocks)
                .add("workNumbers", workNumbers)
                .toString();
    }
}
