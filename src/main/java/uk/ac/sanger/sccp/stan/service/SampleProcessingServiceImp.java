package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.OperationTypeRepo;
import uk.ac.sanger.sccp.stan.repo.SampleRepo;
import uk.ac.sanger.sccp.stan.repo.TissueRepo;
import uk.ac.sanger.sccp.stan.request.AddExternalIDRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

@Service
public class SampleProcessingServiceImp implements SampleProcessingService {
    private final SampleRepo sampleRepo;
    private final TissueRepo tissueRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo labwareRepo;
    private final OperationService opService;
    private final Validator<String> externalNameValidator;

    @Autowired
    public SampleProcessingServiceImp(SampleRepo sampleRepo,
                                      TissueRepo tissueRepo,
                                      OperationTypeRepo opTypeRepo,
                                      LabwareRepo labwareRepo,
                                      OperationService opService,
                                      @Qualifier("externalNameValidator") Validator<String> externalNameValidator) {
        this.sampleRepo = sampleRepo;
        this.tissueRepo = tissueRepo;
        this.opTypeRepo = opTypeRepo;
        this.labwareRepo = labwareRepo;
        this.opService = opService;
        this.externalNameValidator = externalNameValidator;
    }

    @Override
    public OperationResult addExternalID(User user, AddExternalIDRequest request) {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        Collection<String> problems = new LinkedHashSet<>();

        Labware lw = labwareRepo.getByBarcode(request.getLabwareBarcode());
        String externalName = request.getExternalName();
        Set<Sample> samples = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .collect(toSet());

        validateSamples(problems, samples);
        validateExternalName(problems, externalName);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        Tissue tissue = samples.iterator().next().getTissue();
        tissue.setExternalName(externalName);
        OperationType opType = opTypeRepo.getByName("Add external ID");
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        return new OperationResult(List.of(op), List.of(lw));
    }

    public void validateSamples(Collection<String> problems, Set<Sample> samples) {
        if (samples.isEmpty()) {
            problems.add("Could not find a sample associated with this labware");
            return;
        }
        if (samples.size() > 1) {
            problems.add("There are too many samples associated with this labware");
            return;
        }
        Tissue tissue = samples.iterator().next().getTissue();
        if (tissue.getExternalName() != null && !tissue.getExternalName().isEmpty()) {
            problems.add("The associated tissue already has an external identifier: " + tissue.getExternalName());
        }
        if (tissue.getReplicate() == null || tissue.getReplicate().isEmpty()) {
            problems.add("The associated tissue does not have a replicate number");
        }
    }

    public void validateExternalName(Collection<String> problems, String externalName) {
        if (externalName==null || externalName.isEmpty()) {
            problems.add("No external identifier provided");
            return;
        }
        if (!tissueRepo.findAllByExternalName(externalName).isEmpty()) {
            problems.add("External identifier is already associated with another sample: " + externalName);
        }
        externalNameValidator.validate(externalName, problems::add);
    }

}
