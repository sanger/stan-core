package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.SpatialLocation;
import uk.ac.sanger.sccp.stan.model.TissueType;
import uk.ac.sanger.sccp.stan.repo.SpatialLocationRepo;
import uk.ac.sanger.sccp.stan.repo.TissueTypeRepo;
import uk.ac.sanger.sccp.stan.request.AddTissueTypeRequest;
import uk.ac.sanger.sccp.stan.request.AddTissueTypeRequest.NewSpatialLocation;

import java.util.*;
import java.util.function.Consumer;

import static uk.ac.sanger.sccp.utils.BasicUtils.asList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class TissueTypeServiceImp implements TissueTypeService {
    private final TissueTypeRepo ttRepo;
    private final SpatialLocationRepo slRepo;
    private final Validator<String> ttNameValidator, ttCodeValidator, slNameValidator;

    @Autowired
    public TissueTypeServiceImp(TissueTypeRepo ttRepo, SpatialLocationRepo slRepo,
                                @Qualifier("tissueTypeNameValidator") Validator<String> ttNameValidator,
                                @Qualifier("tissueTypeCodeValidator") Validator<String> ttCodeValidator,
                                @Qualifier("spatialLocationNameValidator") Validator<String> slNameValidator) {
        this.ttRepo = ttRepo;
        this.slRepo = slRepo;
        this.ttNameValidator = ttNameValidator;
        this.ttCodeValidator = ttCodeValidator;
        this.slNameValidator = slNameValidator;
    }

    @Override
    public TissueType perform(AddTissueTypeRequest request) {
        sanitise(request);
        Set<String> problems = validate(request);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        return execute(request);
    }

    /**
     * Sanitises the contents of the request.
     * Trims the strings, and capitalises the tissue type code.
     * Skips null values.
     * @param request the request to sanitise
     */
    public void sanitise(AddTissueTypeRequest request) {
        if (request==null) {
            return;
        }
        if (request.getName()!=null) {
            request.setName(request.getName().trim());
        }
        if (request.getCode()!=null) {
            request.setCode(request.getCode().toUpperCase().trim());
        }
        if (request.getSpatialLocations()!=null) {
            for (var sl : request.getSpatialLocations()) {
                if (sl.getName()!=null) {
                    sl.setName(sl.getName().trim());
                }
            }
        }
    }

    /**
     * Checks for problems with the request. Returns any problems found.
     * @param request the request to validate
     * @return descriptions of problems found
     */
    public Set<String> validate(AddTissueTypeRequest request) {
        Set<String> problems = new LinkedHashSet<>();
        if (request==null) {
            problems.add("No request supplied.");
            return problems;
        }
        Consumer<String> addProblem = problems::add;
        ttNameValidator.validate(request.getName(), addProblem);
        ttCodeValidator.validate(request.getCode(), addProblem);
        validateSpatialLocations(problems, request.getSpatialLocations());
        checkExistingTissueTypes(problems, request.getName(), request.getCode());
        return problems;
    }

    /**
     * Checks the spatial locations for problems.
     * @param problems receptacle for problems found
     * @param sls the information about spatial locations to create
     */
    public void validateSpatialLocations(Collection<String> problems, List<NewSpatialLocation> sls) {
        if (nullOrEmpty(sls)) {
            problems.add("No spatial locations specified.");
            return;
        }
        Set<Integer> seenCodes = new HashSet<>(sls.size());
        final Consumer<String> addProblem = problems::add;
        for (NewSpatialLocation sl : sls) {
            if (sl.getCode() < 0) {
                problems.add("Spatial location codes cannot be negative numbers.");
            } else if (!seenCodes.add(sl.getCode())) {
                problems.add("Spatial locations cannot contain duplicate codes.");
            }
            slNameValidator.validate(sl.getName(), addProblem);
        }
    }

    /**
     * Checks if a tissue type already exists with the given name or code
     * @param problems receptacle for problems
     * @param name the name to look for
     * @param code the code to look for
     */
    public void checkExistingTissueTypes(final Collection<String> problems, String name, String code) {
        if (!nullOrEmpty(name)) {
            var optTt = ttRepo.findByName(name);
            if (optTt.isPresent()) {
                problems.add("Tissue type already exists: "+optTt.get().getName());
                return;
            }
        }
        if (!nullOrEmpty(code)) {
            ttRepo.findByCode(code).ifPresent(tt -> problems.add("Tissue type code already in use: "+tt.getCode()));
        }
    }

    /**
     * Creates the new tissue type and spatial locations
     * @param request specification of the new tissue type
     * @return the new tissue type
     */
    public TissueType execute(final AddTissueTypeRequest request) {
        TissueType tissueType = ttRepo.save(new TissueType(null, request.getName(), request.getCode()));
        List<SpatialLocation> sls = request.getSpatialLocations().stream()
                .map(nsl -> new SpatialLocation(null, nsl.getName(), nsl.getCode(), tissueType))
                .toList();
        tissueType.setSpatialLocations(asList(slRepo.saveAll(sls)));
        return tissueType;
    }
}
