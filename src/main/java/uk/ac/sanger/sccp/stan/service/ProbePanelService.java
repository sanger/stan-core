package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.ProbePanel;
import uk.ac.sanger.sccp.stan.model.ProbePanel.ProbeType;
import uk.ac.sanger.sccp.stan.repo.ProbePanelRepo;

import javax.persistence.EntityExistsException;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Service dealing with {@link ProbePanel}s
 * @author dr6
 */
@Service
public class ProbePanelService {
    private final ProbePanelRepo probePanelRepo;
    private final Validator<String> probePanelValidator;

    @Autowired
    public ProbePanelService(ProbePanelRepo probePanelRepo,
                             @Qualifier("probePanelNameValidator") Validator<String> probePanelValidator) {
        this.probePanelRepo = probePanelRepo;
        this.probePanelValidator = probePanelValidator;
    }

    /** Adds a new probe panel. Call this in a transaction. */
    public ProbePanel addProbePanel(ProbeType type, String name) {
        if (type==null) {
            throw new IllegalArgumentException("No probe type supplied.");
        }
        if (name!=null) {
            name = name.trim();
        }
        if (nullOrEmpty(name)) {
            throw new IllegalArgumentException("No name supplied.");
        }
        probePanelValidator.checkArgument(name);
        if (probePanelRepo.existsByTypeAndName(type, name)) {
            throw new EntityExistsException("Probe panel already exists: "+type+" "+name);
        }
        return probePanelRepo.save(new ProbePanel(type, name));
    }

    /** Enables or disables an existing probe panel. Call this in a transaction. */
    public ProbePanel setProbePanelEnabled(ProbeType type, String name, boolean enabled) {
        ProbePanel probe = probePanelRepo.getByTypeAndName(type, name);
        if (probe.isEnabled() == enabled) {
            return probe;
        }
        probe.setEnabled(enabled);
        return probePanelRepo.save(probe);
    }

}
