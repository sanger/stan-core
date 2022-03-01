package uk.ac.sanger.sccp.stan.service.measurements;

import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Factory for {@link SlotMeasurementValidatorImp}.
 * @author dr6
 */
@Service
public class SlotMeasurementValidatorFactory {
    /**
     * Returns a SlotMeasurementValidator with the given valid measurement names.
     * @param validMeasurementNames the valid measurement names
     * @return a slot measurement validator
     */
    public SlotMeasurementValidator getSlotMeasurementValidator(Collection<String> validMeasurementNames) {
        return new SlotMeasurementValidatorImp(validMeasurementNames);
    }
}
