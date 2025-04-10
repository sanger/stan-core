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

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class TissueTypeServiceImp implements TissueTypeService {
    private final TissueTypeRepo ttRepo;
    private final SpatialLocationRepo slRepo;
    private final Validator<String> ttNameValidator, ttCodeValidator, slNameValidator;
    private final Comparator<SpatialLocation> SL_ORDER = Comparator.comparing(SpatialLocation::getCode);

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
    public TissueType performAddTissueType(AddTissueTypeRequest request) {
        sanitise(request);
        Set<String> problems = validateAddTissueType(request);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        return executeAddTissueType(request);
    }

    @Override
    public TissueType performAddSpatialLocations(AddTissueTypeRequest request) {
        sanitise(request);
        Set<String> problems = new LinkedHashSet<>();
        TissueType tt = validateAddSpatialLocations(problems, request);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        return executeAddSpatialLocations(tt, request.getSpatialLocations());
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
    public Set<String> validateAddTissueType(AddTissueTypeRequest request) {
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
     * Checks for problems with the request.
     * @param problems receptacle for problems found
     * @param request the request to validate
     * @return existing tissue type specified
     */
    public TissueType validateAddSpatialLocations(Collection<String> problems, AddTissueTypeRequest request) {
        if (request==null) {
            problems.add("No request supplied.");
            return null;
        }
        validateSpatialLocations(problems, request.getSpatialLocations());
        TissueType tt = loadTissueType(problems, request.getName());
        checkExistingSpatialLocations(problems, tt, request.getSpatialLocations());
        return tt;
    }

    /** Loads an existing tissue type by name. */
    public TissueType loadTissueType(Collection<String> problems, String name) {
        if (name!=null) {
            Optional<TissueType> optTt = ttRepo.findByName(name);
            if (optTt.isPresent()) {
                return optTt.get();
            }
            problems.add("Unknown tissue type name: " + repr(name));
        }
        return null;
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
     * Checks if new spatial locations class with existing ones
     * @param problems receptacle for problems
     * @param tt the tissue type the spatial locations belong to
     * @param newSls details of new spatial locations
     */
    public void checkExistingSpatialLocations(final Collection<String> problems, TissueType tt,
                                              List<NewSpatialLocation> newSls) {
        if (tt==null || nullOrEmpty(tt.getSpatialLocations()) || nullOrEmpty(newSls)) {
            return;
        }
        Set<String> newNamesUC = new HashSet<>(newSls.size());
        Set<Integer> newCodes = new HashSet<>(newSls.size());
        for (NewSpatialLocation sl : newSls) {
            if (!nullOrEmpty(sl.getName())) {
                newNamesUC.add(sl.getName().toUpperCase());
            }
            if (sl.getCode() >= 0) {
                newCodes.add(sl.getCode());
            }
        }
        for (SpatialLocation sl : tt.getSpatialLocations()) {
            if (newNamesUC.contains(sl.getName().toUpperCase())) {
                problems.add("Spatial location already exists: "+sl.getName());
            }
            if (newCodes.contains(sl.getCode())) {
                problems.add("Spatial location code already in use: "+sl.getCode());
            }
        }
    }

    /**
     * Creates the new tissue type and spatial locations
     * @param request specification of the new tissue type
     * @return the new tissue type
     */
    public TissueType executeAddTissueType(final AddTissueTypeRequest request) {
        TissueType tissueType = ttRepo.save(new TissueType(null, request.getName(), request.getCode()));
        List<SpatialLocation> sls = request.getSpatialLocations().stream()
                .map(nsl -> new SpatialLocation(null, nsl.getName(), nsl.getCode(), tissueType))
                .sorted(SL_ORDER)
                .toList();
        tissueType.setSpatialLocations(asList(slRepo.saveAll(sls)));
        return tissueType;
    }

    /**
     * Creates spatial locations for an existing tissue type
     * @param tt existing tissue type
     * @param newSls details of new spatial locations
     * @return updated tissue type
     */
    public TissueType executeAddSpatialLocations(final TissueType tt, final List<NewSpatialLocation> newSls) {
        List<SpatialLocation> sls = newSls.stream()
                .map(nsl -> new SpatialLocation(null, nsl.getName(), nsl.getCode(), tt))
                .sorted(SL_ORDER)
                .toList();
        List<SpatialLocation> existingSls = coalesce(tt.getSpatialLocations(), List.of());
        ArrayList<SpatialLocation> combinedSpatialLocations = new ArrayList<>(existingSls.size() + sls.size());
        combinedSpatialLocations.addAll(existingSls);
        combinedSpatialLocations.addAll(asList(slRepo.saveAll(sls)));
        combinedSpatialLocations.sort(SL_ORDER);
        tt.setSpatialLocations(combinedSpatialLocations);
        return tt;
    }
}
