package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.LabwareType;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Collection;

/**
 * Validates the reagent transfers for a reagent transfer op
 */
public interface ReagentTransferValidatorService {
    /**
     * Checks for problems with the specified transfers. Problems include:<ul>
     * <li>invalid or missing reagent plate barcodes</li>
     * <li>invalid or missing reagent slot addresses</li>
     * <li>invalid or missing destination slot addresses</li>
     * <li>reagent slot addresses already used in a previous operation</li>
     * <li>reagent slot addresses given multiple times in this request</li>
     * </ul>
     * @param problems receptacle for problems
     * @param transfers the transfers to validate
     * @param reagentPlates the existing reagent plates
     * @param lt the destination labware type, if known
     */
    void validateTransfers(Collection<String> problems, Collection<ReagentTransfer> transfers,
                           UCMap<ReagentPlate> reagentPlates, LabwareType lt);
}
