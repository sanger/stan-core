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
    private final Work work;

    public ValidatedSections(UCMap<LabwareType> labwareTypes, UCMap<Donor> donorMap,
                             UCMap<Sample> sampleMap, Work work) {
        this.labwareTypes = labwareTypes;
        this.donorMap = donorMap;
        this.sampleMap = sampleMap;
        this.work = work;
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

    /** The work specified in the request */
    public Work getWork() {
        return this.work;
    }
}
