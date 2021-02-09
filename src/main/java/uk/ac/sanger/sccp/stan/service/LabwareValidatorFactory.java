package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Labware;

import java.util.Collection;

/**
 * @author dr6
 */
@Service
public class LabwareValidatorFactory {
    public LabwareValidator getValidator(Collection<Labware> labware) {
        return new LabwareValidator(labware);
    }

    public LabwareValidator getValidator() {
        return new LabwareValidator();
    }
}
