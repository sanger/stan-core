package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
// This might have been a nice idea, but in practice it's unnecessarily complicated.
public class RegisterValidationImp implements RegisterValidation {
    private final RegisterRequest request;
    private final DonorRepo donorRepo;
    private final HmdmcRepo hmdmcRepo;
    private final TissueTypeRepo ttRepo;
    private final LabwareTypeRepo ltRepo;
    private final MediumRepo mediumRepo;
    private final FixativeRepo fixativeRepo;
    private final TissueRepo tissueRepo;
    private final SpeciesRepo speciesRepo;
    private final Validator<String> donorNameValidation;
    private final Validator<String> externalNameValidation;
    private final Validator<String> replicateValidator;
    private final TissueFieldChecker tissueFieldChecker;
    private final WorkService workService;

    final Map<String, Donor> donorMap = new HashMap<>();
    final Map<String, Tissue> tissueMap = new HashMap<>();
    final Map<String, Hmdmc> hmdmcMap = new HashMap<>();
    final Map<String, Species> speciesMap = new HashMap<>();
    final Map<StringIntKey, SpatialLocation> spatialLocationMap = new HashMap<>();
    final Map<String, LabwareType> labwareTypeMap = new HashMap<>();
    final Map<String, Medium> mediumMap = new HashMap<>();
    final Map<String, Fixative> fixativeMap = new HashMap<>();
    Collection<Work> works;
    final LinkedHashSet<String> problems = new LinkedHashSet<>();

    public RegisterValidationImp(RegisterRequest request, DonorRepo donorRepo,
                                 HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo, LabwareTypeRepo ltRepo,
                                 MediumRepo mediumRepo,
                                 FixativeRepo fixativeRepo, TissueRepo tissueRepo, SpeciesRepo speciesRepo,
                                 Validator<String> donorNameValidation, Validator<String> externalNameValidation,
                                 Validator<String> replicateValidator,
                                 TissueFieldChecker tissueFieldChecker, WorkService workService) {
        this.request = request;
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.tissueRepo = tissueRepo;
        this.speciesRepo = speciesRepo;
        this.donorNameValidation = donorNameValidation;
        this.externalNameValidation = externalNameValidation;
        this.replicateValidator = replicateValidator;
        this.tissueFieldChecker = tissueFieldChecker;
        this.workService = workService;
    }

    @Override
    public Collection<String> validate() {
        if (blocks().isEmpty()) {
            return Collections.emptySet(); // nothing to do
        }
        validateDonors();
        validateHmdmcs();
        validateSpatialLocations();
        validateLabwareTypes();
        validateMediums();
        validateFixatives();
        validateCollectionDates();
        validateExistingTissues();
        validateNewTissues();
        validateWorks();
        return problems;
    }

    public void validateDonors() {
        for (BlockRegisterRequest block : blocks()) {
            boolean skip = false;
            Species species = null;
            if (block.getDonorIdentifier()==null || block.getDonorIdentifier().isEmpty()) {
                skip = true;
                addProblem("Missing donor identifier.");
            } else if (donorNameValidation!=null) {
                donorNameValidation.validate(block.getDonorIdentifier(), this::addProblem);
            }
            if (block.getSpecies()==null || block.getSpecies().isEmpty()) {
                addProblem("Missing species.");
            } else {
                String speciesUc = block.getSpecies().toUpperCase();
                species = speciesMap.get(speciesUc);
                if (species==null && !speciesMap.containsKey(speciesUc)) {
                    species = speciesRepo.findByName(speciesUc).orElse(null);
                    speciesMap.put(speciesUc, species);
                    if (species==null) {
                        addProblem("Unknown species: "+repr(block.getSpecies()));
                    } else if (!species.isEnabled()) {
                        addProblem("Species is not enabled: "+species.getName());
                    }
                }
            }
            if (skip) {
                continue;
            }
            String donorNameUc = block.getDonorIdentifier().toUpperCase();
            Donor donor = donorMap.get(donorNameUc);
            if (donor==null) {
                donor = new Donor(null, block.getDonorIdentifier(), block.getLifeStage(), species);
                donorMap.put(donorNameUc, donor);
            } else {
                if (donor.getLifeStage()!=block.getLifeStage()) {
                    addProblem("Multiple different life stages specified for donor "+donor.getDonorName());
                }
                if (species!=null && !species.equals(donor.getSpecies())) {
                    addProblem("Multiple different species specified for donor "+donor.getDonorName());
                }
            }
        }
        for (Map.Entry<String, Donor> entry : donorMap.entrySet()) {
            Optional<Donor> optDonor = donorRepo.findByDonorName(entry.getKey());
            if (optDonor.isEmpty()) {
                continue;
            }
            Donor realDonor = optDonor.get();
            Donor newDonor = entry.getValue();
            if (realDonor.getLifeStage()!=newDonor.getLifeStage()) {
                addProblem("Wrong life stage given for existing donor "+realDonor.getDonorName());
            }
            if (newDonor.getSpecies()!=null && !newDonor.getSpecies().equals(realDonor.getSpecies())) {
                addProblem("Wrong species given for existing donor "+realDonor.getDonorName());
            }
            entry.setValue(realDonor);
        }
    }

    public void validateSpatialLocations() {
        Map<String, TissueType> tissueTypeMap = new HashMap<>();
        Set<String> unknownTissueTypes = new LinkedHashSet<>();
        for (BlockRegisterRequest block : blocks()) {
            if (block.getTissueType()==null || block.getTissueType().isEmpty()) {
                addProblem("Missing tissue type.");
                continue;
            }
            if (unknownTissueTypes.contains(block.getTissueType())) {
                continue;
            }
            StringIntKey key = new StringIntKey(block.getTissueType(), block.getSpatialLocation());
            if (spatialLocationMap.containsKey(key)) {
                continue;
            }
            TissueType tt = tissueTypeMap.get(key.string);
            if (tt==null) {
                Optional<TissueType> ttOpt = ttRepo.findByName(key.string);
                if (ttOpt.isEmpty()) {
                    unknownTissueTypes.add(block.getTissueType());
                    continue;
                }
                tt = ttOpt.get();
                tissueTypeMap.put(key.string, tt);
            }
            final int slCode = block.getSpatialLocation();
            Optional<SpatialLocation> slOpt = tt.getSpatialLocations().stream()
                    .filter(spl -> spl.getCode()==slCode)
                    .findAny();
            if (slOpt.isEmpty()) {
                addProblem(String.format("Unknown spatial location %s for tissue type %s.", slCode, tt.getName()));
                continue;
            }
            spatialLocationMap.put(key, slOpt.get());
        }
        if (!unknownTissueTypes.isEmpty()) {
            if (unknownTissueTypes.size()==1) {
                addProblem("Unknown tissue type: "+unknownTissueTypes);
            } else {
                addProblem("Unknown tissue types: " + unknownTissueTypes);
            }
        }
    }

    public void validateHmdmcs() {
        Set<String> unknownHmdmcs = new LinkedHashSet<>();
        boolean unwanted = false;
        boolean missing = false;
        for (BlockRegisterRequest block : blocks()) {
            boolean needsHmdmc = false;
            boolean needsNoHmdmc = false;
            if (block.getSpecies()!=null && !block.getSpecies().isEmpty()) {
                needsHmdmc = block.getSpecies().equalsIgnoreCase("Human");
                needsNoHmdmc = !needsHmdmc;
            }
            String hmdmcString = block.getHmdmc();
            if (hmdmcString==null || hmdmcString.isEmpty()) {
                if (needsHmdmc) {
                    missing = true;
                }
                continue;
            }
            if (needsNoHmdmc) {
                unwanted = true;
                continue;
            }

            String hmdmcUc = hmdmcString.toUpperCase();
            if (hmdmcMap.containsKey(hmdmcUc)) {
                continue;
            }
            Hmdmc hmdmc = hmdmcRepo.findByHmdmc(hmdmcString).orElse(null);
            hmdmcMap.put(hmdmcUc, hmdmc);
            if (hmdmc==null) {
                unknownHmdmcs.add(hmdmcString);
            }
        }
        if (missing) {
            addProblem("Missing HuMFre number.");
        }
        if (unwanted) {
            addProblem("Non-human tissue should not have a HuMFre number.");
        }
        if (!unknownHmdmcs.isEmpty()) {
            addProblem(pluralise("Unknown HuMFre number{s}: ", unknownHmdmcs.size()) + unknownHmdmcs);
        }
        List<String> disabledHmdmcs = hmdmcMap.values().stream()
                .filter(h -> h!=null && !h.isEnabled())
                .map(Hmdmc::getHmdmc)
                .toList();
        if (!disabledHmdmcs.isEmpty()) {
            addProblem(pluralise("HuMFre number{s} not enabled: ", disabledHmdmcs.size()) + disabledHmdmcs);
        }
    }

    public void validateLabwareTypes() {
        validateByName("labware type", BlockRegisterRequest::getLabwareType, ltRepo::findByName, labwareTypeMap);
    }

    public void validateMediums() {
        validateByName("medium", BlockRegisterRequest::getMedium, mediumRepo::findByName, mediumMap);
    }

    public void validateFixatives() {
        validateByName("fixative", BlockRegisterRequest::getFixative, fixativeRepo::findByName, fixativeMap);
    }

    public void validateCollectionDates() {
        boolean missing = false;
        LocalDate today = LocalDate.now();
        Set<LocalDate> badDates = new LinkedHashSet<>();
        Map<String, LocalDate> extToDate = new HashMap<>(request.getBlocks().size());
        for (BlockRegisterRequest block : blocks()) {
            if (block.getSampleCollectionDate()==null) {
                if (block.getLifeStage()==LifeStage.fetal && block.getSpecies()!=null
                        && block.getSpecies().equalsIgnoreCase("Human")) {
                    missing = true;
                }
            } else if (block.getSampleCollectionDate().isAfter(today)) {
                badDates.add(block.getSampleCollectionDate());
            }
            if (block.getExternalIdentifier()!=null && !block.getExternalIdentifier().isEmpty()) {
                String key = block.getExternalIdentifier().trim().toUpperCase();
                if (!key.isEmpty()) {
                    if (extToDate.containsKey(key) && !Objects.equals(extToDate.get(key), block.getSampleCollectionDate())) {
                        addProblem("Inconsistent collection dates specified for tissue " + key + ".");
                    } else {
                        extToDate.put(key, block.getSampleCollectionDate());
                    }
                }
            }
        }
        if (missing) {
            addProblem("Human fetal samples must have a collection date.");
        }
        if (!badDates.isEmpty()) {
            addProblem(pluralise("Invalid sample collection date{s}: ", badDates.size()) + badDates);
        }
    }

    public void validateExistingTissues() {
        List<BlockRegisterRequest> blocksForExistingTissues = blocks().stream()
                .filter(BlockRegisterRequest::isExistingTissue)
                .toList();
        if (blocksForExistingTissues.isEmpty()) {
            return;
        }
        if (blocksForExistingTissues.stream().map(BlockRegisterRequest::getExternalIdentifier).anyMatch(xn -> xn==null || xn.isEmpty())) {
            addProblem("Missing external identifier.");
        }
        Set<String> xns = blocksForExistingTissues.stream()
                .map(BlockRegisterRequest::getExternalIdentifier)
                .filter(Objects::nonNull)
                .collect(toLinkedHashSet());
        if (xns.isEmpty()) {
            return;
        }
        tissueRepo.findAllByExternalNameIn(xns).forEach(t -> tissueMap.put(t.getExternalName().toUpperCase(), t));

        Set<String> missing = xns.stream()
                .filter(xn -> !tissueMap.containsKey(xn.toUpperCase()))
                .collect(toLinkedHashSet());
        if (!missing.isEmpty()) {
            addProblem("Existing external identifiers not recognised: " + reprCollection(missing));
        }

        for (BlockRegisterRequest br : blocksForExistingTissues) {
            String xn = br.getExternalIdentifier();
            if (xn == null || xn.isEmpty()) {
                continue;
            }
            Tissue tissue = tissueMap.get(xn.toUpperCase());
            if (tissue!=null) {
                tissueFieldChecker.check(this::addProblem, br, tissue);
            }
        }
    }

    public void validateNewTissues() {
        // NB repeated new external identifier in one request is still disallowed
        Set<String> externalNames = new HashSet<>();
        for (BlockRegisterRequest block : blocks()) {
            if (block.isExistingTissue()) {
                continue;
            }
            if (block.getReplicateNumber()==null || block.getReplicateNumber().isEmpty()) {
                addProblem("Missing replicate number.");
            } else {
                replicateValidator.validate(block.getReplicateNumber(), this::addProblem);
            }
            if (block.getHighestSection() < 0) {
                addProblem("Highest section number cannot be negative.");
            }
            if (block.getExternalIdentifier()==null || block.getExternalIdentifier().isEmpty()) {
                addProblem("Missing external identifier.");
            } else {
                if (externalNameValidation != null) {
                    externalNameValidation.validate(block.getExternalIdentifier(), this::addProblem);
                }
                if (!externalNames.add(block.getExternalIdentifier().toUpperCase())) {
                    addProblem("Repeated external identifier: " + block.getExternalIdentifier());
                } else if (!tissueRepo.findAllByExternalName(block.getExternalIdentifier()).isEmpty()) {
                    addProblem(String.format("There is already tissue in the database with external identifier %s.",
                            block.getExternalIdentifier()));
                }
            }
        }
    }

    public void validateWorks() {
        if (request.getWorkNumbers().isEmpty()) {
            addProblem("No work number supplied.");
            works = List.of();
        } else {
            works = workService.validateUsableWorks(problems, request.getWorkNumbers()).values();
        }
    }

    private <E> void validateByName(String entityName,
                                    Function<BlockRegisterRequest, String> nameFunction,
                                    Function<String, Optional<E>> lkp,
                                    Map<String, E> map) {
        Set<String> unknownNames = new LinkedHashSet<>();
        boolean missing = false;
        for (BlockRegisterRequest block : blocks()) {
            String name = nameFunction.apply(block);
            if (name==null || name.isEmpty()) {
                missing = true;
                continue;
            }
            if (unknownNames.contains(name)) {
                continue;
            }
            String nameUc = name.toUpperCase();
            if (map.containsKey(nameUc)) {
                continue;
            }
            Optional<E> opt = lkp.apply(nameUc);
            if (opt.isEmpty()) {
                unknownNames.add(name);
                continue;
            }
            map.put(nameUc, opt.get());
        }
        if (missing) {
            addProblem(String.format("Missing %s.", entityName));
        }
        if (!unknownNames.isEmpty()) {
            addProblem(String.format("Unknown %s%s: %s", entityName, unknownNames.size()==1 ? "" : "s", unknownNames));
        }
    }

    private Collection<BlockRegisterRequest> blocks() {
        return request.getBlocks();
    }

    private boolean addProblem(String problem) {
        return problems.add(problem);
    }

    public Collection<String> getProblems() {
        return this.problems;
    }

    @Override
    public Donor getDonor(String name) {
        return ucGet(this.donorMap, name);
    }

    @Override
    public Hmdmc getHmdmc(String hmdmc) {
        return ucGet(hmdmcMap, hmdmc);
    }

    @Override
    public SpatialLocation getSpatialLocation(String tissueTypeName, int code) {
        return (tissueTypeName==null ? null : this.spatialLocationMap.get(new StringIntKey(tissueTypeName, code)));
    }

    @Override
    public LabwareType getLabwareType(String name) {
        return ucGet(this.labwareTypeMap, name);
    }

    @Override
    public Medium getMedium(String name) {
        return ucGet(this.mediumMap, name);
    }

    @Override
    public Fixative getFixative(String name) {
        return ucGet(this.fixativeMap, name);
    }

    @Override
    public Tissue getTissue(String externalName) {
        return ucGet(this.tissueMap, externalName);
    }

    @Override
    public Collection<Work> getWorks() {
        return this.works;
    }

    private static <E> E ucGet(Map<String, E> map, String key) {
        return (key==null ? null : map.get(key.toUpperCase()));
    }

    record StringIntKey(String string, int number) {
        StringIntKey(String string, int number) {
            this.string = string.toUpperCase();
            this.number = number;
        }
    }
}
