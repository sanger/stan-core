package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.UCMap;

/**
 * The output of successful section register validation.
 * @author dr6
 */
public record ValidatedSections(UCMap<LabwareType> labwareTypes, UCMap<Donor> donorMap, UCMap<Sample> sampleMap,
                                UCMap<SlotRegion> slotRegionMap, UCMap<BioRisk> bioRiskMap, Work work) {
}
