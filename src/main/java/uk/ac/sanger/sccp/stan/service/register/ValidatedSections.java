package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.UCMap;

/**
 * The output of successful section register validation.
 * @author dr6
 */
class ValidatedSections {
    private final UCMap<LabwareType> labwareTypes;
    private final UCMap<Donor> donorMap;
    private final UCMap<Sample> sampleMap;

    public ValidatedSections(UCMap<LabwareType> labwareTypes, UCMap<Donor> donorMap,
                             UCMap<Sample> sampleMap) {
        this.labwareTypes = labwareTypes;
        this.donorMap = donorMap;
        this.sampleMap = sampleMap;
    }

    /** The labware types, mapped from their name. */
    public UCMap<LabwareType> getLabwareTypes() {
        return this.labwareTypes;
    }

    /** The donors, a mixture of persisted and new (unpersisted), mapped from their name. */
    public UCMap<Donor> getDonorMap() {
        return this.donorMap;
    }

    /** The samples to be created, mapped from the external identifier. */
    public UCMap<Sample> getSampleMap() {
        return this.sampleMap;
    }
}
