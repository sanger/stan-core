package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

@Service
public class OriginalSampleRegisterServiceImp implements IRegisterService<OriginalSampleRegisterRequest> {
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
    private final CellClassRepo cellClassRepo;

    private final Validator<String> donorNameValidator;
    private final Validator<String> externalNameValidator;
    private final Validator<String> hmdmcValidator;
    private final Validator<String> replicateValidator;

    private final LabwareService labwareService;
    private final OperationService opService;
    private final WorkService workService;
    private final BioRiskService bioRiskService;

    @Autowired
    public OriginalSampleRegisterServiceImp(DonorRepo donorRepo, TissueRepo tissueRepo,
                                            TissueTypeRepo tissueTypeRepo, SampleRepo sampleRepo,
                                            BioStateRepo bsRepo, SlotRepo slotRepo,
                                            HmdmcRepo hmdmcRepo, SpeciesRepo speciesRepo, FixativeRepo fixativeRepo,
                                            MediumRepo mediumRepo, SolutionRepo solutionRepo, LabwareTypeRepo ltRepo,
                                            OperationTypeRepo opTypeRepo, OperationSolutionRepo opSolutionRepo,
                                            CellClassRepo cellClassRepo,
                                            @Qualifier("donorNameValidator") Validator<String> donorNameValidator,
                                            @Qualifier("externalNameValidator") Validator<String> externalNameValidator,
                                            @Qualifier("hmdmcValidator") Validator<String> hmdmcValidator,
                                            @Qualifier("replicateValidator") Validator<String> replicateValidator,
                                            LabwareService labwareService, OperationService opService,
                                            WorkService workService, BioRiskService bioRiskService) {
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
        this.cellClassRepo = cellClassRepo;
        this.donorNameValidator = donorNameValidator;
        this.externalNameValidator = externalNameValidator;
        this.hmdmcValidator = hmdmcValidator;
        this.replicateValidator = replicateValidator;
        this.sampleRepo = sampleRepo;
        this.labwareService = labwareService;
        this.opService = opService;
        this.workService = workService;
        this.bioRiskService = bioRiskService;
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
        checkFormat(problems, request, "Life stage", OriginalSampleData::getLifeStage, false, null);
        checkFormat(problems, request, "HuMFre number", OriginalSampleData::getHmdmc, false, hmdmcValidator);
        checkFormat(problems, request, "Replicate number", OriginalSampleData::getReplicateNumber, false, replicateValidator);
        checkFormat(problems, request, "Species", OriginalSampleData::getSpecies, true, null);
        checkFormat(problems, request, "Tissue type", OriginalSampleData::getTissueType, true, null);
        checkFormat(problems, request, "Spatial location", OriginalSampleData::getSpatialLocation, true, null);
        checkFormat(problems, request, "Fixative", OriginalSampleData::getFixative, true, null);
        checkFormat(problems, request, "Solution", OriginalSampleData::getSolution, true, null);
        checkFormat(problems, request, "Labware type", OriginalSampleData::getLabwareType, true, null);
        checkCollectionDates(problems, request);

        List<DataStruct> datas = request.getSamples().stream().map(DataStruct::new).collect(toList());
        checkExistence(problems, datas, "HuMFre number", OriginalSampleData::getHmdmc, hmdmcRepo::findByHmdmc, DataStruct::setHmdmc);
        checkExistence(problems, datas, "species", OriginalSampleData::getSpecies, speciesRepo::findByName, DataStruct::setSpecies);
        checkExistence(problems, datas, "fixative", OriginalSampleData::getFixative, fixativeRepo::findByName, DataStruct::setFixative);
        checkExistence(problems, datas, "solution", OriginalSampleData::getSolution, solutionRepo::findByName, DataStruct::setSolution);
        checkExistence(problems, datas, "labware type", OriginalSampleData::getLabwareType, ltRepo::findByName, DataStruct::setLabwareType);
        checkExistence(problems, datas, "cellular classification", OriginalSampleData::getCellClass, cellClassRepo::findByName, DataStruct::setCellClass);
        checkHmdmcsForSpecies(problems, datas);
        checkWorks(problems, datas);
        loadDonors(datas);
        checkExternalNamesUnique(problems, request);
        checkDonorFieldsAreConsistent(problems, datas);
        checkTissueTypesAndSpatialLocations(problems, datas);
        checkBioRisks(problems, datas);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request validation failed.", problems);
        }

        createNewDonors(datas);
        createNewSamples(datas);
        createNewLabware(datas);
        recordRegistrations(user, datas);
        recordSolutions(datas);
        linkWork(datas);
        linkBioRisks(datas);
        return makeResult(datas);
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

    <T> void checkExistence(Collection<String> problems, List<DataStruct> datas, String fieldName,
                                   Function<OriginalSampleData, String> function,
                                   Function<String, Optional<T>> repoFunction,
                                   BiConsumer<DataStruct, T> setter) {
        UCMap<T> entities = new UCMap<>();
        Set<String> unknownValues = new LinkedHashSet<>();
        for (DataStruct data : datas) {
            String value = function.apply(data.getOriginalSampleData());
            if (!nullOrEmpty(value)) {
                if (entities.containsKey(value)) {
                    T entity = entities.get(value);
                    setter.accept(data, entity);
                } else {
                    Optional<T> opt = repoFunction.apply(value);
                    if (opt.isEmpty()) {
                        unknownValues.add(value);
                    } else {
                        setter.accept(data, opt.get());
                    }
                    entities.put(value, opt.orElse(null));
                }
            }
        }
        if (!unknownValues.isEmpty()) {
            problems.add("Unknown "+fieldName+": "+unknownValues);
        }
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
     * @param datas the registration data under construction
     */
    void checkDonorFieldsAreConsistent(Collection<String> problems, List<DataStruct> datas) {
        for (DataStruct data : datas) {
            Donor donor = data.donor;
            if (donor==null) {
                continue;
            }
            if (data.getOriginalSampleData().getLifeStage()!=null && data.getOriginalSampleData().getLifeStage() != donor.getLifeStage()) {
                problems.add("Donor life stage inconsistent with existing donor "+donor.getDonorName()+".");
            }
            if (!nullOrEmpty(data.getOriginalSampleData().getSpecies())
                && !data.getOriginalSampleData().getSpecies().equalsIgnoreCase(donor.getSpecies().getName())) {
                problems.add("Donor species inconsistent with existing donor "+donor.getDonorName()+".");
            }
        }

        UCMap<LifeStage> donorNameToLifeStage = new UCMap<>();
        UCMap<String> donorNameToSpecies = new UCMap<>();
        for (DataStruct data : datas) {
            String donorName = data.getOriginalSampleData().getDonorIdentifier();
            if (nullOrEmpty(donorName) || data.donor!=null) {
                continue;
            }
            LifeStage lifeStage = data.getOriginalSampleData().getLifeStage();
            if (lifeStage!=null) {
                if (donorNameToLifeStage.get(donorName)==null) {
                    donorNameToLifeStage.put(donorName, lifeStage);
                } else if (lifeStage != donorNameToLifeStage.get(donorName)) {
                    problems.add("Multiple life stages specified for donor "+donorName+".");
                }
            }
            String speciesName = data.getOriginalSampleData().getSpecies();
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
     * Check that HMDMCs are given for human tissue samples (and not for nonhuman samples)
     * @param problems receptacle for problems
     * @param datas data for the register request
     */
    public void checkHmdmcsForSpecies(Collection<String> problems, Collection<DataStruct> datas) {
        boolean anyUnexpected = false;
        boolean anyMissing = false;
        for (var data : datas) {
            if (data.species == null || data.cellClass == null) {
                continue;
            }
            boolean gotHmdmc = !nullOrEmpty(data.originalSampleData.getHmdmc());
            if (gotHmdmc && !data.species.requiresHmdmc()) {
                anyUnexpected = true;
            }
            if (!gotHmdmc && data.species.requiresHmdmc() && data.cellClass.isHmdmcRequired()) {
                anyMissing = true;
            }
        }
        if (anyUnexpected) {
            problems.add("HuMFre number not expected for non-human samples.");
        }
        if (anyMissing) {
            problems.add("HuMFre number missing for human tissue samples.");
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
                problems.add("External name already used: " + tissues.stream().map(Tissue::getExternalName).toList());
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
     * @param datas data under construction
     */
    void checkTissueTypesAndSpatialLocations(Collection<String> problems, List<DataStruct> datas) {
        Set<String> tissueTypeNames = datas.stream()
                .map(data -> data.getOriginalSampleData().getTissueType())
                .filter(name -> name!=null && !name.isEmpty())
                .map(String::toUpperCase)
                .collect(toSet());
        if (tissueTypeNames.isEmpty()) {
            return;
        }
        UCMap<TissueType> ttMap = UCMap.from(tissueTypeRepo.findAllByNameIn(tissueTypeNames), TissueType::getName);
        Set<String> unknownTissueTypeNames = new LinkedHashSet<>();
        for (DataStruct data : datas) {
            final String ttName = data.getOriginalSampleData().getTissueType();
            if (nullOrEmpty(ttName)) {
                continue;
            }
            final Integer slCode = data.getOriginalSampleData().getSpatialLocation();
            TissueType tt = ttMap.get(ttName);
            SpatialLocation sl = getSpatialLocation(tt, slCode);
            if (tt==null) {
                unknownTissueTypeNames.add(repr(ttName));
            } else if (slCode != null && sl==null) {
                problems.add("There is no spatial location "+slCode+" for tissue type "+tt.getName()+".");
            }
            data.spatialLocation = sl;
        }
        if (!unknownTissueTypeNames.isEmpty()) {
            problems.add("Unknown tissue type: "+unknownTissueTypeNames);
        }
    }

    void checkWorks(Collection<String> problems, List<DataStruct> datas) {
        Set<String> workNumbers = datas.stream()
                .map(ds -> ds.originalSampleData.getWorkNumber())
                .filter(wn -> !nullOrEmpty(wn))
                .map(String::toUpperCase)
                .collect(toSet());
        if (workNumbers.isEmpty()) {
            return;
        }

        UCMap<Work> works = workService.validateUsableWorks(problems, workNumbers);
        for (DataStruct data : datas) {
            data.work = works.get(data.originalSampleData.getWorkNumber());
        }
    }

    /**
     * Checks bio risk codes are specified and correspond to known bio risks
     * @param problems receptacle for problems
     * @param datas data in progress
     */
    void checkBioRisks(Collection<String> problems, List<DataStruct> datas) {
        UCMap<BioRisk> riskMap = bioRiskService.loadAndValidateBioRisks(problems, datas.stream().map(DataStruct::getOriginalSampleData),
                OriginalSampleData::getBioRiskCode, OriginalSampleData::setBioRiskCode);
        if (!riskMap.isEmpty()) {
            for (DataStruct data : datas) {
                data.setBioRisk(riskMap.get(data.originalSampleData.getBioRiskCode()));
            }
        }
    }

    /**
     * Loads any existing donors matching given donor names.
     * The donors are placed in the appropriate field in the DataStructs.
     * @param datas the data under construction
     */
    void loadDonors(List<DataStruct> datas) {
        Set<String> donorNames = datas.stream()
                .map(data -> data.getOriginalSampleData().getDonorIdentifier())
                .filter(dn -> !nullOrEmpty(dn))
                .collect(toSet());
        if (donorNames.isEmpty()) {
            return;
        }
        UCMap<Donor> donorMap = UCMap.from(donorRepo.findAllByDonorNameIn(donorNames), Donor::getDonorName);
        for (DataStruct data : datas) {
            data.donor = donorMap.get(data.getOriginalSampleData().getDonorIdentifier());
        }
    }

    /**
     * Creates donors that do not already exist.
     * @param datas the request data under construction
     */
    void createNewDonors(List<DataStruct> datas) {
        List<Donor> newDonors = datas.stream()
                .filter(data -> data.donor==null)
                .filter(BasicUtils.distinctBySerial(data -> data.getOriginalSampleData().getDonorIdentifier().toUpperCase()))
                .map(data -> new Donor(null, data.getOriginalSampleData().getDonorIdentifier(),
                        data.getOriginalSampleData().getLifeStage(), data.species))
                .collect(toList());
        if (newDonors.isEmpty()) {
            return;
        }
        final Iterable<Donor> savedDonors = donorRepo.saveAll(newDonors);
        UCMap<Donor> donorMap = new UCMap<>(newDonors.size());
        savedDonors.forEach(d -> donorMap.put(d.getDonorName(), d));
        for (DataStruct data : datas) {
            if (data.donor==null) {
                data.donor = donorMap.get(data.getOriginalSampleData().getDonorIdentifier());
            }
        }
    }

    /**
     * Creates new tissues and samples in the database
     * @param datas the data under construction
     */
    void createNewSamples(List<DataStruct> datas) {
        final Medium medium = mediumRepo.getByName("None");
        BioState cassetteBs = bsRepo.getByName("Tissue");
        BioState nonCassetteBs = bsRepo.getByName("Original sample");

        for (DataStruct data : datas) {
            OriginalSampleData req = data.getOriginalSampleData();
            Tissue createdTissue = tissueRepo.save(new Tissue(
                    null, emptyToNull(req.getExternalIdentifier()),
                    emptyToNull(req.getReplicateNumber()),
                    data.spatialLocation, data.donor, medium, data.fixative, data.cellClass, data.hmdmc,
                    req.getSampleCollectionDate(),null
            ));
            BioState bs = (data.labwareType.getName().equalsIgnoreCase("Cassette") ? cassetteBs : nonCassetteBs);
            data.sample = sampleRepo.save(new Sample(null, null, createdTissue, bs));
        }
    }

    /**
     * Records specified solutions against the given operations
     * @param datas created data
     */
    void recordSolutions(List<DataStruct> datas) {
        Collection<OperationSolution> opSols = new LinkedHashSet<>();
        for (DataStruct data : datas) {
            if (data.solution!=null) {
                Operation op = data.operation;
                for (Action a : op.getActions()) {
                    opSols.add(new OperationSolution(op.getId(), data.solution.getId(),
                            a.getDestination().getLabwareId(), a.getSample().getId()));
                }
            }
        }
        if (!opSols.isEmpty()) {
            opSolutionRepo.saveAll(opSols);
        }
    }

    /**
     * Link operations and labware to the indicated work.
     * @param datas created data
     */
    void linkWork(List<DataStruct> datas) {
        Stream<WorkService.WorkOp> workOps = datas.stream()
                .filter(data -> data.work!=null && data.operation!=null)
                .map(data -> new WorkService.WorkOp(data.work, data.operation));
        workService.linkWorkOps(workOps);
    }

    /**
     * Link samples and operations to the indicated bio risk
     * @param datas created data
     */
    void linkBioRisks(List<DataStruct> datas) {
        for (DataStruct data : datas) {
            bioRiskService.recordSampleBioRisks(Map.of(data.sample.getId(), data.bioRisk), data.operation.getId());
        }
    }

    /**
     * Creates labware for each data element
     * @param datas the data under construction
     */
    void createNewLabware(List<DataStruct> datas) {
        for (DataStruct data : datas) {
            final Labware lw = labwareService.create(data.labwareType);
            final Slot slot = lw.getFirstSlot();
            slot.addSample(data.sample);
            slotRepo.save(slot);
            data.labware = lw;
        }
    }

    /**
     * Records registration ops using {@link OperationService}.
     * @param user user responsible for operations
     * @param datas data under construction
     */
    void recordRegistrations(User user, List<DataStruct> datas) {
        OperationType opType = opTypeRepo.getByName("Register");
        for (var data : datas) {
            data.operation = opService.createOperationInPlace(opType, user, data.labware, null, null);
        }
    }

    /**
     * Creates a registration result from the given created data objects.
     * @param datas created data objects
     * @return a result including the labware and solution names
     */
    RegisterResult makeResult(List<DataStruct> datas) {
        List<Labware> lwList = new ArrayList<>(datas.size());
        List<LabwareSolutionName> lwSols = new ArrayList<>(datas.size());
        for (DataStruct data : datas) {
            lwList.add(data.labware);
            if (data.solution!=null) {
                lwSols.add(new LabwareSolutionName(data.labware.getBarcode(), data.solution.getName()));
            }
        }
        return new RegisterResult(lwList, lwSols);
    }

    /**
     * A structure wrapping the data requested and the entities found and created to meet the request
     */
    static class DataStruct {
        OriginalSampleData originalSampleData;
        Hmdmc hmdmc;
        SpatialLocation spatialLocation;
        LabwareType labwareType;
        Solution solution;
        Fixative fixative;
        Species species;
        Work work;
        BioRisk bioRisk;
        CellClass cellClass;

        Donor donor;
        Sample sample;
        Labware labware;
        Operation operation;

        DataStruct(OriginalSampleData originalSampleData) {
            this.originalSampleData = originalSampleData;
        }

        OriginalSampleData getOriginalSampleData() {
            return this.originalSampleData;
        }

        void setHmdmc(Hmdmc hmdmc) {
            this.hmdmc = hmdmc;
        }

        void setLabwareType(LabwareType labwareType) {
            this.labwareType = labwareType;
        }

        void setSolution(Solution solution) {
            this.solution = solution;
        }

        void setFixative(Fixative fixative) {
            this.fixative = fixative;
        }

        void setSpecies(Species species) {
            this.species = species;
        }

        public void setWork(Work work) {
            this.work = work;
        }

        void setBioRisk(BioRisk risk) {
            this.bioRisk = risk;
        }

        void setCellClass(CellClass cellClass) {
            this.cellClass = cellClass;
        }
    }
}
