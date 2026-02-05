package uk.ac.sanger.sccp.stan.request.register;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A request to register new blocks of tissue.
 * @author dr6
 */
public class BlockRegisterRequest {
    private List<String> workNumbers = List.of();
    private List<BlockRegisterLabware> labware = List.of();

    public BlockRegisterRequest() {} // required

    public BlockRegisterRequest(List<String> workNumbers, List<BlockRegisterLabware> labware) {
        setWorkNumbers(workNumbers);
        setLabware(labware);
    }

    /** The work numbers for the request. */
    public List<String> getWorkNumbers() {
        return this.workNumbers;
    }

    public void setWorkNumbers(List<String> workNumbers) {
        this.workNumbers = nullToEmpty(workNumbers);
    }

    /** The labware to register. */
    public List<BlockRegisterLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<BlockRegisterLabware> labware) {
        this.labware = nullToEmpty(labware);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("workNumbers", workNumbers)
                .add("labware", labware)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        BlockRegisterRequest that = (BlockRegisterRequest) o;
        return (Objects.equals(this.workNumbers, that.workNumbers)
                && Objects.equals(this.labware, that.labware)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumbers, labware);
    }
}