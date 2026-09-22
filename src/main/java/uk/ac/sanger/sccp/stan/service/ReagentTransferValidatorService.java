package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Layout;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.Collection;
import java.util.List;

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
     * @param layout the layout of the destination labware, if known
     */
    default void validateTransfers(Collection<String> problems, Collection<ReagentTransfer> transfers,
                           UCMap<ReagentPlate> reagentPlates, Layout layout) {
        validateTransfers(problems, reagentPlates, List.of(new LayoutTransfers(layout, transfers)));
    }

    /**
     * Checks for problems with the specified transfers. Problems include:<ul>
     * <li>invalid or missing reagent plate barcodes</li>
     * <li>invalid or missing reagent slot addresses</li>
     * <li>invalid or missing destination slot addresses</li>
     * <li>reagent slot addresses already used in a previous operation</li>
     * <li>reagent slot addresses given multiple times in this request</li>
     * </ul>
     * @param problems receptacle for problems
     * @param reagentPlates the existing reagent plates
     * @param layoutTransfers the layouts of the destination labware, if known, along with the relevant transfers
     */
    void validateTransfers(Collection<String> problems, UCMap<ReagentPlate> reagentPlates, Collection<LayoutTransfers> layoutTransfers);

    /** A layout and the relevant transfers */
    record LayoutTransfers(Layout layout, Collection<ReagentTransfer> transfers) {}
}
