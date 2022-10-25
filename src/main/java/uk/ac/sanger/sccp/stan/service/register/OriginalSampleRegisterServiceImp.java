package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

@Service
public class OriginalSampleRegisterServiceImp implements OriginalSampleRegisterService {
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final TissueTypeRepo tissueTypeRepo;
    private final SampleRepo sampleRepo;
    private final BioStateRepo bsRepo;
    private final SlotRepo slotRepo;
    private final HmdmcRepo hmdmcRepo;
    private final SpeciesRepo speciesRepo;
    private final FixativeRepo fixativeRepo;
    private final MediumRepo mediumRepo;

    private final SolutionRepo solutionRepo;
    private final LabwareTypeRepo ltRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationSolutionRepo opSolutionRepo;


    private final Validator<String> donorNameValidator;
    private final Validator<String> externalNameValidator;
    private final Validator<String> hmdmcValidator;
    private final Validator<String> replicateValidator;

    private final LabwareService labwareService;
    private final OperationService opService;

    @Autowired
    public OriginalSampleRegisterServiceImp(DonorRepo donorRepo, TissueRepo tissueRepo,
                                            TissueTypeRepo tissueTypeRepo, SampleRepo sampleRepo,
                                            BioStateRepo bsRepo, SlotRepo slotRepo,
                                            HmdmcRepo hmdmcRepo, SpeciesRepo speciesRepo, FixativeRepo fixativeRepo,
                                            MediumRepo mediumRepo, SolutionRepo solutionRepo, LabwareTypeRepo ltRepo,
                                            OperationTypeRepo opTypeRepo, OperationSolutionRepo opSolutionRepo,
                                            @Qualifier("donorNameValidator") Validator<String> donorNameValidator,
                                            @Qualifier("externalNameValidator") Validator<String> externalNameValidator,
                                            @Qualifier("hmdmcValidator") Validator<String> hmdmcValidator,
                                            @Qualifier("replicateValidator") Validator<String> replicateValidator,
                                            LabwareService labwareService, OperationService opService) {
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.tissueTypeRepo = tissueTypeRepo;
        this.bsRepo = bsRepo;
        this.slotRepo = slotRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.speciesRepo = speciesRepo;
        this.fixativeRepo = fixativeRepo;
        this.mediumRepo = mediumRepo;
        this.solutionRepo = solutionRepo;
        this.ltRepo = ltRepo;
        this.opTypeRepo = opTypeRepo;
        this.opSolutionRepo = opSolutionRepo;
        this.donorNameValidator = donorNameValidator;
        this.externalNameValidator = externalNameValidator;
        this.hmdmcValidator = hmdmcValidator;
        this.replicateValidator = replicateValidator;
        this.sampleRepo = sampleRepo;
        this.labwareService = labwareService;
        this.opService = opService;
    }

    @Override
    public RegisterResult register(User user, OriginalSampleRegisterRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        Collection<String> problems = new LinkedHashSet<>();

        if (request.getSamples().isEmpty()) {
            problems.add("No samples specified in request.");
            throw new ValidationException("The request validation failed.", problems);
        }

        checkFormat(problems, request, "Donor identifier", OriginalSampleData::getDonorIdentifier, true, donorNameValidator);
        checkFormat(problems, request, "External identifier", OriginalSampleData::getExternalIdentifier, false, externalNameValidator);
        checkFormat(problems, request, "Life stage", OriginalSampleData::getLifeStage, true, null);
        checkFormat(problems, request, "HuMFre number", OriginalSampleData::getHmdmc, false, hmdmcValidator);
        checkFormat(problems, request, "Replicate number", OriginalSampleData::getReplicateNumber, false, replicateValidator);
        checkFormat(problems, request, "Species", OriginalSampleData::getSpecies, true, null);
        checkFormat(problems, request, "Tissue type", OriginalSampleData::getTissueType, true, null);
        checkFormat(problems, request, "Spatial location", OriginalSampleData::getSpatialLocation, true, null);
        checkFormat(problems, request, "Fixative", OriginalSampleData::getFixative, true, null);
        checkFormat(problems, request, "Solution", OriginalSampleData::getSolution, true, null);
        checkFormat(problems, request, "Labware type", OriginalSampleData::getLabwareType, true, null);
        checkHmdmcsForSpecies(problems, request);
        checkCollectionDates(problems, request);

        UCMap<Hmdmc> hmdmcs = checkExistence(problems, request, "HuMFre number", OriginalSampleData::getHmdmc, hmdmcRepo::findByHmdmc);
        UCMap<Species> species = checkExistence(problems, request, "species", OriginalSampleData::getSpecies, speciesRepo::findByName);
        UCMap<Fixative> fixatives = checkExistence(problems, request, "fixative", OriginalSampleData::getFixative, fixativeRepo::findByName);
        UCMap<Solution> solutions = checkExistence(problems, request, "solution", OriginalSampleData::getSolution, solutionRepo::findByName);
        UCMap<LabwareType> lwTypes = checkExistence(problems, request, "labware type", OriginalSampleData::getLabwareType, ltRepo::findByName);

        UCMap<Donor> donors = loadDonors(request);
        checkExternalNamesUnique(problems, request);
        checkDonorFieldsAreConsistent(problems, request, donors);

        UCMap<TissueType> tissueTypes = checkTissueTypesAndSpatialLocations(problems, request);

        // NB It is allowed to have the same spatial location and donor multiple times
        // checkDonorSpatialLocationUnique(problems, request, donors, tissueTypes);

        checkNoneIdentical(problems, request.getSamples());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request validation failed.", problems);
        }

        createNewDonors(request, donors, species);
        var tissues = createNewTissues(request, donors, tissueTypes, hmdmcs, fixatives);

        Map<OriginalSampleData, Sample> samples = createSamples(request, tissues);
        Map<OriginalSampleData, Labware> labware = createLabware(request, lwTypes, samples);
        var ops = recordRegistrations(user, labware);
        var opSols = recordSolutions(ops, solutions);
        var lwSolNames = composeLabwareSolutionNames(labware, opSols);

        return new RegisterResult(new ArrayList<>(labware.values()), lwSolNames);
    }

    /**
     * Checks a field for common problems.
     * @param problems receptacle for found problems
     * @param request the register request
     * @param fieldName the name of the field (for problem messages)
     * @param function the function to get the field value from the register data
     * @param required is the field required (is it a problem for it to be missing)?
     * @param formatValidator the format validator (if any) that checks if the field format is correct
     * @param <T> the type of the field value
     */
    public <T> void checkFormat(Collection<String> problems, OriginalSampleRegisterRequest request,
                                String fieldName, Function<OriginalSampleData, T> function,
                                boolean required, Validator<T> formatValidator) {
        boolean anyMissing = false;
        for (OriginalSampleData data : request.getSamples()) {
            T value = function.apply(data);
            if (value==null || (value instanceof String && ((String) value).isEmpty())) {
                anyMissing = true;
            } else if (formatValidator!=null) {
                formatValidator.validate(value, problems::add);
            }
        }
        if (required && anyMissing) {
            problems.add(fieldName+" missing.");
        }
    }

    /**
     * Checks for the existence of some value specified in the request
     * @param problems receptacle for found problems
     * @param request the register request
     * @param fieldName the name of the field (for problem messages)
     * @param function the function to get the field value from the register data
     * @param repoFunction the function to look up the value in a repo
     * @return a map of strings to the found entities
     * @param <T> the type of entity
     */
    public <T> UCMap<T> checkExistence(Collection<String> problems, OriginalSampleRegisterRequest request,
                                       String fieldName, Function<OriginalSampleData, String> function,
                                       Function<String, Optional<T>> repoFunction) {
        UCMap<T> entities = new UCMap<>();
        Set<String> unknownValues = new LinkedHashSet<>();
        for (OriginalSampleData data : request.getSamples()) {
            String value = function.apply(data);
            if (!nullOrEmpty(value)) {
                if (entities.containsKey(value)) {
                    continue;
                }
                Optional<T> opt = repoFunction.apply(value);
                if (opt.isEmpty()) {
                    unknownValues.add(value);
                } else {
                    entities.put(value, opt.get());
                }
            }
        }
        if (!unknownValues.isEmpty()) {
            problems.add("Unknown "+fieldName+": "+unknownValues);
        }
        return entities;
    }

    /**
     * Checks for invalid or missing collection dates.
     * @param problems receptacle for problems
     * @param request the registration request
     */
    public void checkCollectionDates(Collection<String> problems, OriginalSampleRegisterRequest request) {
        boolean anyMissing = false;
        boolean anyInFuture = false;
        LocalDate today = LocalDate.now();
        for (var data : request.getSamples()) {
            if (data.getSampleCollectionDate()!=null) {
                if (data.getSampleCollectionDate().isAfter(today)) {
                    anyInFuture = true;
                }
            } else if (data.getLifeStage()==LifeStage.fetal && "human".equalsIgnoreCase(data.getSpecies())) {
                anyMissing = true;
            }
        }
        if (anyInFuture) {
            problems.add("Collection date must be in the past.");
        }
        if (anyMissing) {
            problems.add("Collection date is required for fetal samples.");
        }
    }

    /**
     * Checks that fields given for donors match existing donor records and are consistent inside the request
     * @param problems receptacle for problems
     * @param request the register request
     * @param donors the existing donors
     */
    public void checkDonorFieldsAreConsistent(Collection<String> problems, OriginalSampleRegisterRequest request,
                                              UCMap<Donor> donors) {
        for (OriginalSampleData data : request.getSamples()) {
            Donor donor = donors.get(data.getDonorIdentifier());
            if (donor==null) {
                continue;
            }
            if (data.getLifeStage()!=null && data.getLifeStage()!=donor.getLifeStage()) {
                problems.add("Donor life stage inconsistent with existing donor "+donor.getDonorName()+".");
            }
            if (!nullOrEmpty(data.getSpecies()) && !data.getSpecies().equalsIgnoreCase(donor.getSpecies().getName())) {
                problems.add("Donor species inconsistent with existing donor "+donor.getDonorName()+".");
            }
        }

        UCMap<LifeStage> donorNameToLifeStage = new UCMap<>();
        UCMap<String> donorNameToSpecies = new UCMap<>();
        for (OriginalSampleData data : request.getSamples()) {
            String donorName = data.getDonorIdentifier();
            if (nullOrEmpty(donorName) || donors.get(donorName)!=null) {
                continue;
            }
            LifeStage lifeStage = data.getLifeStage();
            if (lifeStage!=null) {
                if (donorNameToLifeStage.get(donorName)==null) {
                    donorNameToLifeStage.put(donorName, lifeStage);
                } else if (lifeStage != donorNameToLifeStage.get(donorName)) {
                    problems.add("Multiple life stages specified for donor "+donorName+".");
                }
            }
            String speciesName = data.getSpecies();
            if (!nullOrEmpty(speciesName)) {
                if (donorNameToSpecies.get(donorName)==null) {
                    donorNameToSpecies.put(donorName, speciesName);
                } else if (!speciesName.equalsIgnoreCase(donorNameToSpecies.get(donorName))) {
                    problems.add("Multiple species specified for donor "+donorName+".");
                }
            }
        }
    }

    /**
     * Check that HMDMCs are given for human samples (and not for nonhuman samples)
     * @param problems receptacle for problems
     * @param request the register request
     */
    public void checkHmdmcsForSpecies(Collection<String> problems, OriginalSampleRegisterRequest request) {
        boolean anyUnexpected = false;
        boolean anyMissing = false;
        for (var data : request.getSamples()) {
            if (nullOrEmpty(data.getSpecies())) {
                continue;
            }
            boolean needHmdmc = data.getSpecies().equalsIgnoreCase("human");
            boolean gotHmdmc = !nullOrEmpty(data.getHmdmc());
            if (needHmdmc && !gotHmdmc) {
                anyMissing = true;
            }
            if (gotHmdmc && !needHmdmc) {
                anyUnexpected = true;
            }
        }
        if (anyUnexpected) {
            problems.add("HuMFre number not expected for non-human samples.");
        }
        if (anyMissing) {
            problems.add("HuMFre number missing for human samples.");
        }
    }

    /**
     * Checks that tissue external names given in the request are unique, and that they are not already in use
     * @param problems receptacle for problems
     * @param request register request
     */
    public void checkExternalNamesUnique(Collection<String> problems, OriginalSampleRegisterRequest request) {
        Set<String> externalNamesUC = new LinkedHashSet<>();
        Set<String> repeated = new LinkedHashSet<>();
        for (OriginalSampleData data : request.getSamples()) {
            String xn = data.getExternalIdentifier();
            if (!nullOrEmpty(xn)) {
                if (!externalNamesUC.add(xn.toUpperCase())) {
                    repeated.add(xn);
                }
            }
        }
        if (!repeated.isEmpty()) {
            problems.add("External names repeated: "+repeated);
        }
        if (!externalNamesUC.isEmpty()) {
            var tissues = tissueRepo.findAllByExternalNameIn(externalNamesUC);
            if (!tissues.isEmpty()) {
                problems.add("External name already used: " + tissues.stream().map(Tissue::getExternalName).collect(toList()));
            }
        }
    }


    /**
     * Gets spatial location from its tissue type
     * @param tissueType the tissue type for the spatial location
     * @param slCode the code of the spatial location
     * @return the matching spatial location, or null
     */
    public SpatialLocation getSpatialLocation(TissueType tissueType, Integer slCode) {
        if (tissueType==null || slCode==null) {
            return null;
        }
        final List<SpatialLocation> sls = tissueType.getSpatialLocations();
        // If the sl are sensibly arranged, this should work:
        if (slCode>=0 && slCode < sls.size()) {
            SpatialLocation sl = sls.get(slCode);
            if (sl.getCode().equals(slCode)) {
                return sl;
            }
        }
        return sls.stream()
                .filter(sl -> sl.getCode().equals(slCode))
                .findAny()
                .orElse(null);
    }

    /**
     * Looks up the tissue types and checks the spatial locations are valid
     * @param problems receptacle for problems
     * @param request register request
     * @return a map of tissue types from their names
     */
    public UCMap<TissueType> checkTissueTypesAndSpatialLocations(Collection<String> problems, OriginalSampleRegisterRequest request) {
        Set<String> tissueTypeNames = request.getSamples().stream()
                .map(OriginalSampleData::getTissueType)
                .filter(name -> name!=null && !name.isEmpty())
                .map(String::toUpperCase)
                .collect(toSet());
        if (tissueTypeNames.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<TissueType> ttMap = UCMap.from(tissueTypeRepo.findAllByNameIn(tissueTypeNames), TissueType::getName);
        Set<String> unknownTissueTypeNames = new LinkedHashSet<>();
        for (OriginalSampleData data : request.getSamples()) {
            final String ttName = data.getTissueType();
            if (nullOrEmpty(ttName)) {
                continue;
            }
            final Integer slCode = data.getSpatialLocation();
            TissueType tt = ttMap.get(ttName);
            if (tt==null) {
                unknownTissueTypeNames.add(repr(ttName));
            } else if (slCode != null && getSpatialLocation(tt, slCode)==null) {
                problems.add("There is no spatial location "+slCode+" for tissue type "+tt.getName()+".");
            }
        }
        if (!unknownTissueTypeNames.isEmpty()) {
            problems.add("Unknown tissue type: "+unknownTissueTypeNames);
        }
        return ttMap;
    }

    /**
     * Checks that none of the sample data in the request are identical, because the logic herein will not work if
     * it is.
     * @param problems receptacle for problems
     * @param samples the request data
     */
    public void checkNoneIdentical(Collection<String> problems, Collection<OriginalSampleData> samples) {
        if (new HashSet<>(samples).size() < samples.size()) {
            problems.add("Multiple completely identical samples specified in request.");
        }
    }

    /**
     * Loads existing donors from the database
     * @param request the register request
     * @return a map of donors from their names
     */
    public UCMap<Donor> loadDonors(OriginalSampleRegisterRequest request) {
        Set<String> donorNames = request.getSamples().stream()
                .map(OriginalSampleData::getDonorIdentifier)
                .filter(dn -> !nullOrEmpty(dn))
                .collect(toSet());
        if (donorNames.isEmpty()) {
            return new UCMap<>();
        }
        var donors = donorRepo.findAllByDonorNameIn(donorNames);
        return UCMap.from(donors, Donor::getDonorName);
    }

    /**
     * Creates donors that do not already exist.
     * The new donors are added to the given map
     * @param request register request
     * @param donors map of donors from their names
     * @param species map to look up species
     */
    public void createNewDonors(OriginalSampleRegisterRequest request, UCMap<Donor> donors, UCMap<Species> species) {
        List<Donor> newDonors = request.getSamples().stream()
                .filter(data -> donors.get(data.getDonorIdentifier())==null)
                .map(data -> new Donor(null, data.getDonorIdentifier(), data.getLifeStage(), species.get(data.getSpecies())))
                .filter(BasicUtils.distinctBySerial(d -> d.getDonorName().toUpperCase()))
                .collect(toList());

        for (Donor donor : donorRepo.saveAll(newDonors)) {
            donors.put(donor.getDonorName(), donor);
        }
    }

    /**
     * Creates new tissues in the database
     * @param request the registration request
     * @param donors the donors (already in the db)
     * @param tissueTypes map to look up tissue types
     * @param hmdmcs map to look up hmdmcs
     * @param fixatives map to look up fixatives
     * @return a map of tissues from their external name
     */
    public Map<OriginalSampleData, Tissue> createNewTissues(OriginalSampleRegisterRequest request, UCMap<Donor> donors,
                                          UCMap<TissueType> tissueTypes, UCMap<Hmdmc> hmdmcs,
                                          UCMap<Fixative> fixatives) {
        Medium medium = mediumRepo.getByName("None");

        Map<OriginalSampleData, Tissue> map = new HashMap<>(request.getSamples().size());

        for (OriginalSampleData data : request.getSamples()) {
            Tissue tissue = new Tissue(null, emptyToNull(data.getExternalIdentifier()),
                    emptyToNull(data.getReplicateNumber()),
                    getSpatialLocation(tissueTypes.get(data.getTissueType()), data.getSpatialLocation()),
                    donors.get(data.getDonorIdentifier()),
                    medium, fixatives.get(data.getFixative()),
                    hmdmcs.get(data.getHmdmc()),
                    data.getSampleCollectionDate(),
                    null
            );
            map.put(data, tissueRepo.save(tissue));
        }
        return map;
    }

    /**
     * Link the operations (and labware and samples) to the solutions used
     * @param operations the operations, mapped from their parts of the request
     * @param solutions the solutions, mapped from their names
     */
    public Map<OriginalSampleData, Solution> recordSolutions(Map<OriginalSampleData, Operation> operations, UCMap<Solution> solutions) {
        Collection<OperationSolution> opSols = new LinkedHashSet<>();
        Map<OriginalSampleData, Solution> opSolMap = new HashMap<>(operations.size());
        for (var entry : operations.entrySet()) {
            OriginalSampleData osd = entry.getKey();
            Operation op = entry.getValue();
            if (!nullOrEmpty(osd.getSolution())) {
                Solution solution = solutions.get(osd.getSolution());
                for (Action ac : op.getActions()) {
                    opSols.add(new OperationSolution(op.getId(), solution.getId(),
                            ac.getDestination().getLabwareId(), ac.getSample().getId()));
                }
                opSolMap.put(osd, solution);
            }
        }
        if (!opSols.isEmpty()) {
            opSolutionRepo.saveAll(opSols);
        }
        return opSolMap;
    }

    /**
     * Creates new samples
     * @param request the register request
     * @param tissues the tissues, already created
     * @return a map of the new samples from the corresponding part of the request
     */
    public Map<OriginalSampleData, Sample> createSamples(OriginalSampleRegisterRequest request,
                                                         Map<OriginalSampleData, Tissue> tissues) {
        BioState bs = bsRepo.getByName("Original sample");
        Map<OriginalSampleData, Sample> samples = new HashMap<>(request.getSamples().size());
        for (OriginalSampleData data : request.getSamples()) {
            Tissue tissue = tissues.get(data);
            Sample sample = new Sample(null, null, tissue, bs);
            samples.put(data, sampleRepo.save(sample));
        }
        return samples;
    }

    /**
     * Creates new labware containing the created samples
     * @param request the register request
     * @param labwareTypes map to look up labware types
     * @param samples the new samples
     * @return a map of labware from the corresponding parts of the request
     */
    public Map<OriginalSampleData, Labware> createLabware(OriginalSampleRegisterRequest request,
                                                          UCMap<LabwareType> labwareTypes,
                                                          Map<OriginalSampleData, Sample> samples) {
        Map<OriginalSampleData, Labware> labware = new HashMap<>(request.getSamples().size());
        for (OriginalSampleData data : request.getSamples()) {
            Sample sample = samples.get(data);
            LabwareType lt = labwareTypes.get(data.getLabwareType());
            Labware lw = labwareService.create(lt);
            final Slot slot = lw.getFirstSlot();
            slot.addSample(sample);
            slotRepo.save(slot);
            labware.put(data, lw);
        }
        return labware;
    }

    /**
     * Records register operations
     * @param user the user responsible for the operations
     * @param labware the map of request to labware
     * @return the new operations mapped from the original sample data request
     */
    public Map<OriginalSampleData, Operation> recordRegistrations(User user, Map<OriginalSampleData, Labware> labware) {
        Map<OriginalSampleData, Operation> opMap = new HashMap<>(labware.size());
        OperationType opType = opTypeRepo.getByName("Register");
        for (var entry : labware.entrySet()) {
            Labware lw = entry.getValue();
            opMap.put(entry.getKey(), opService.createOperationInPlace(opType, user, lw, null, null));
        }
        return opMap;
    }

    /**
     * Creates a list of labware barcodes and solution names.
     * @param labware the map of sample data to labware
     * @param opSols the map of sample data to solution
     * @return a list of objects containing a labware barcode and a solution name
     */
    public List<LabwareSolutionName> composeLabwareSolutionNames(Map<OriginalSampleData, Labware> labware, Map<OriginalSampleData, Solution> opSols) {
        return opSols.entrySet().stream()
                .filter(entry -> entry.getValue()!=null)
                .map(entry -> new LabwareSolutionName(labware.get(entry.getKey()).getBarcode(), entry.getValue().getName()))
                .collect(toList());
    }
}
