package uk.ac.sanger.sccp.stan.service.block;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.LabwareType;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockLabware;

import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
public class BlockLabwareData {
    private TissueBlockLabware requestLabware;
    private LabwareType lwType;
    private Labware labware;
    private List<BlockData> blocks;

    public BlockLabwareData(TissueBlockLabware requestLabware) {
        this.requestLabware = requestLabware;
        this.blocks = requestLabware.getContents().stream().map(BlockData::new).toList();
    }

    public TissueBlockLabware getRequestLabware() {
        return this.requestLabware;
    }

    public void setRequestLabware(TissueBlockLabware requestLabware) {
        this.requestLabware = requestLabware;
    }

    public LabwareType getLwType() {
        return this.lwType;
    }

    public void setLwType(LabwareType lwType) {
        this.lwType = lwType;
    }

    public Labware getLabware() {
        return this.labware;
    }

    public void setLabware(Labware labware) {
        this.labware = labware;
    }

    public List<BlockData> getBlocks() {
        return this.blocks;
    }

    public void setBlocks(List<BlockData> blocks) {
        this.blocks = blocks;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BlockLabwareData that = (BlockLabwareData) o;
        return (Objects.equals(this.requestLabware, that.requestLabware)
                && Objects.equals(this.lwType, that.lwType)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.blocks, that.blocks));
    }

    @Override
    public int hashCode() {
        return requestLabware.hashCode();
    }
}
