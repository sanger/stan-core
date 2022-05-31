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
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

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

    private final SolutionSampleRepo solutionRepo;
    private final LabwareTypeRepo ltRepo;
    private final OperationTypeRepo opTypeRepo;


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
                                            MediumRepo mediumRepo, SolutionSampleRepo solutionRepo, LabwareTypeRepo ltRepo,
                                            OperationTypeRepo opTypeRepo,

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
        checkFormat(problems, request, "Solution sample", OriginalSampleData::getSolutionSample, true, null);
        checkFormat(problems, request, "Labware type", OriginalSampleData::getLabwareType, true, null);
        checkHmdmcsForSpecies(problems, request);
        checkCollectionDates(problems, request);

        UCMap<Hmdmc> hmdmcs = checkExistence(problems, request, "HuMFre number", OriginalSampleData::getHmdmc, hmdmcRepo::findByHmdmc);
        UCMap<Species> species = checkExistence(problems, request, "species", OriginalSampleData::getSpecies, speciesRepo::findByName);
        UCMap<Fixative> fixatives = checkExistence(problems, request, "fixative", OriginalSampleData::getFixative, fixativeRepo::findByName);
        UCMap<SolutionSample> solutions = checkExistence(problems, request, "solution sample", OriginalSampleData::getSolutionSample, solutionRepo::findByName);
        UCMap<LabwareType> lwTypes = checkExistence(problems, request, "labware type", OriginalSampleData::getLabwareType, ltRepo::findByName);

        UCMap<Donor> donors = loadDonors(request);
        checkExternalNamesUnique(problems, request);
        checkDonorFieldsAreConsistent(problems, request, donors);

        UCMap<TissueType> tissueTypes = checkTissueTypesAndSpatialLocations(problems, request);

        checkDonorSpatialLocationUnique(problems, request, donors, tissueTypes);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request validation failed.", problems);
        }

        createNewDonors(request, donors, species);
        var tissues = createNewTissues(request, donors, tissueTypes, hmdmcs, fixatives, solutions);

        Map<OriginalSampleData, Sample> samples = createSamples(request, tissues);
        Map<OriginalSampleData, Labware> labware = createLabware(request, lwTypes, samples);
        recordRegistrations(user, labware.values());

        return new RegisterResult(new ArrayList<>(labware.values()));
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
     * Checks that the same spatial location is not indicated multiple times for the same donor (or already
     * exists in the database)
     * @param problems receptacle for problems
     * @param request register request
     * @param donors map to look up donors
     * @param tissueTypeMap map to look up tissue types
     */
    public void checkDonorSpatialLocationUnique(Collection<String> problems, OriginalSampleRegisterRequest request,
                                                UCMap<Donor> donors, UCMap<TissueType> tissueTypeMap) {
        Set<TissueKey> keys = new HashSet<>(request.getSamples().size());
        Set<TissueKey> repeated = new LinkedHashSet<>();
        for (OriginalSampleData data : request.getSamples()) {
            if (nullOrEmpty(data.getDonorIdentifier()) || nullOrEmpty(data.getTissueType())
                || data.getSpatialLocation()==null) {
                continue;
            }
            TissueKey key = new TissueKey(data.getDonorIdentifier(), data.getTissueType(), data.getSpatialLocation());
            if (!keys.add(key)) {
                repeated.add(key);
            }
        }
        if (!repeated.isEmpty()) {
            problems.add("Same donor name, tissue type and spatial location specified multiple times: "+repeated);
        }

        for (TissueKey key : keys) {
            Donor donor = donors.get(key.donorName);
            if (donor==null) {
                continue;
            }
            TissueType tt = tissueTypeMap.get(key.tissueTypeName);
            if (tt==null) {
                continue;
            }
            SpatialLocation sl = getSpatialLocation(tt, key.slCode);
            if (sl==null) {
                continue;
            }
            var tissueClashes = tissueRepo.findAllByDonorIdAndSpatialLocationId(donor.getId(), sl.getId());
            if (!tissueClashes.isEmpty()) {
                Tissue tissue = tissueClashes.get(0);
                problems.add("Tissue from donor "+tissue.getDonor().getDonorName()+", "+tissue.getTissueType().getName()
                        +", spatial location "+sl.getCode()+" already exists in the database.");
            }
        }
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
     * @param solutions map to look up solution samples
     * @return a map of tissues from their external name
     */
    public Map<OriginalSampleData, Tissue> createNewTissues(OriginalSampleRegisterRequest request, UCMap<Donor> donors,
                                          UCMap<TissueType> tissueTypes, UCMap<Hmdmc> hmdmcs,
                                          UCMap<Fixative> fixatives, UCMap<SolutionSample> solutions) {
        Medium medium = mediumRepo.getByName("None");

        Map<OriginalSampleData, Tissue> map = new HashMap<>(request.getSamples().size());

        for (OriginalSampleData data : request.getSamples()) {
            Tissue tissue = new Tissue(null, nullOrEmpty(data.getExternalIdentifier()) ? null : data.getExternalIdentifier(),
                    nullOrEmpty(data.getReplicateNumber()) ? null : data.getReplicateNumber(),
                    getSpatialLocation(tissueTypes.get(data.getTissueType()), data.getSpatialLocation()),
                    donors.get(data.getDonorIdentifier()),
                    medium, fixatives.get(data.getFixative()),
                    hmdmcs.get(data.getHmdmc()),
                    data.getSampleCollectionDate(),
                    solutions.get(data.getSolutionSample()),
                    null
            );
            map.put(data, tissueRepo.save(tissue));
        }
        return map;
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
     * @param labware the newly created labware
     * @return the new operations
     */
    public List<Operation> recordRegistrations(User user, Collection<Labware> labware) {
        List<Operation> ops = new ArrayList<>(labware.size());
        OperationType opType = opTypeRepo.getByName("Register");
        for (Labware lw : labware) {
            ops.add(opService.createOperationInPlace(opType, user, lw, null, null));
        }
        return ops;
    }

    /**
     * The unique fields associated with a tissue
     */
    static class TissueKey {
        /** The name of the donor */
        String donorName;
        String donorNameUpperCase;
        /** The name of the tissue type */
        String tissueTypeName;
        String tissueTypeNameUpperCase;
        /** The spatial location code */
        int slCode;

        public TissueKey(String donorName, String tissueTypeName, int slCode) {
            this.donorName = donorName;
            this.donorNameUpperCase = donorName.toUpperCase();
            this.tissueTypeName = tissueTypeName;
            this.tissueTypeNameUpperCase = tissueTypeName.toUpperCase();
            this.slCode = slCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TissueKey that = (TissueKey) o;
            return (this.slCode == that.slCode
                    && Objects.equals(this.donorNameUpperCase, that.donorNameUpperCase)
                    && Objects.equals(this.tissueTypeNameUpperCase, that.tissueTypeNameUpperCase));
        }

        @Override
        public int hashCode() {
            return Objects.hash(donorNameUpperCase, tissueTypeNameUpperCase, slCode);
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s)", donorName, tissueTypeName, slCode);
        }
    }

}