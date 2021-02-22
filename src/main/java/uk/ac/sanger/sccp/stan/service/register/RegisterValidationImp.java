package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.*;
import java.util.function.Function;

import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

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
    private final MouldSizeRepo mouldSizeRepo;
    private final MediumRepo mediumRepo;
    private final FixativeRepo fixativeRepo;
    private final TissueRepo tissueRepo;
    private final SpeciesRepo speciesRepo;
    private final Validator<String> donorNameValidation;
    private final Validator<String> externalNameValidation;

    final Map<String, Donor> donorMap = new HashMap<>();
    final Map<String, Hmdmc> hmdmcMap = new HashMap<>();
    final Map<String, Species> speciesMap = new HashMap<>();
    final Map<StringIntKey, SpatialLocation> spatialLocationMap = new HashMap<>();
    final Map<String, LabwareType> labwareTypeMap = new HashMap<>();
    final Map<String, MouldSize> mouldSizeMap = new HashMap<>();
    final Map<String, Medium> mediumMap = new HashMap<>();
    final Map<String, Fixative> fixativeMap = new HashMap<>();
    final LinkedHashSet<String> problems = new LinkedHashSet<>();

    public RegisterValidationImp(RegisterRequest request, DonorRepo donorRepo,
                                 HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo, LabwareTypeRepo ltRepo,
                                 MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo,
                                 FixativeRepo fixativeRepo, TissueRepo tissueRepo, SpeciesRepo speciesRepo,
                                 Validator<String> donorNameValidation, Validator<String> externalNameValidation) {
        this.request = request;
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.tissueRepo = tissueRepo;
        this.speciesRepo = speciesRepo;
        this.donorNameValidation = donorNameValidation;
        this.externalNameValidation = externalNameValidation;
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
        validateMouldSizes();
        validateMediums();
        validateFixatives();
        validateTissues();
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
            if (block.getLifeStage()==null) {
                addProblem("Missing life stage.");
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
                if (block.getLifeStage()!=null && donor.getLifeStage()!=block.getLifeStage()) {
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
            if (newDonor.getLifeStage()!=null && realDonor.getLifeStage()!=newDonor.getLifeStage()) {
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
            addProblem("Missing HMDMC number.");
        }
        if (unwanted) {
            addProblem("Non-human tissue should not have an HMDMC number.");
        }
        if (!unknownHmdmcs.isEmpty()) {
            addProblem(pluralise("Unknown HMDMC number{s}: ", unknownHmdmcs.size()) + unknownHmdmcs);
        }
    }

    public void validateLabwareTypes() {
        validateByName("labware type", BlockRegisterRequest::getLabwareType, ltRepo::findByName, labwareTypeMap);
    }

    public void validateMouldSizes() {
        validateByName("mould size", BlockRegisterRequest::getMouldSize, mouldSizeRepo::findByName, mouldSizeMap);
    }

    public void validateMediums() {
        validateByName("medium", BlockRegisterRequest::getMedium, mediumRepo::findByName, mediumMap);
    }

    public void validateFixatives() {
        validateByName("fixative", BlockRegisterRequest::getFixative, fixativeRepo::findByName, fixativeMap);
    }

    public void validateTissues() {
        Set<String> externalNames = new HashSet<>();
        Set<TissueKey> tissueKeys = new HashSet<>();
        for (BlockRegisterRequest block : blocks()) {
            if (block.getReplicateNumber() < 0) {
                addProblem("Replicate number cannot be negative.");
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
                } else if (tissueRepo.findByExternalName(block.getExternalIdentifier()).isPresent()) {
                    addProblem(String.format("There is already tissue in the database with external identifier %s.",
                            block.getExternalIdentifier()));
                }
            }
            TissueKey tissueKey = new TissueKey(block);
            if (tissueKey.isComplete()) {
                if (!tissueKeys.add(tissueKey)) {
                    addProblem("Repeated combination of fields: "+tissueKey);
                } else if (anySimilarTissuesInDatabase(block.getDonorIdentifier(), block.getTissueType(), block.getSpatialLocation(),
                        block.getMedium(), block.getFixative(), block.getReplicateNumber())) {
                    addProblem("There is already similar tissue in the database: "+tissueKey);
                }
            }
        }
    }

    public boolean anySimilarTissuesInDatabase(String donorName, String tissueTypeName, int spatialLocationCode,
                                               String mediumName, String fixativeName, int replicate) {
        Donor donor = getDonor(donorName);
        if (donor==null || donor.getId()==null) {
            return false;
        }
        if (tissueTypeName==null || tissueTypeName.isEmpty()) {
            return false;
        }
        SpatialLocation sl = spatialLocationMap.get(new StringIntKey(tissueTypeName, spatialLocationCode));
        if (sl==null) {
            return false;
        }
        Medium medium = getMedium(mediumName);
        if (medium==null) {
            return false;
        }
        Fixative fixative = getFixative(fixativeName);
        if (fixative==null) {
            return false;
        }
        return tissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(
                donor.getId(), sl.getId(), medium.getId(), fixative.getId(), replicate).isPresent();
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
    public MouldSize getMouldSize(String name) {
        return ucGet(this.mouldSizeMap, name);
    }

    @Override
    public Medium getMedium(String name) {
        return ucGet(this.mediumMap, name);
    }

    @Override
    public Fixative getFixative(String name) {
        return ucGet(this.fixativeMap, name);
    }

    private static <E> E ucGet(Map<String, E> map, String key) {
        return (key==null ? null : map.get(key.toUpperCase()));
    }

    static class StringIntKey {
        String string;
        int number;

        public StringIntKey(String string, int number) {
            this.string = string.toUpperCase();
            this.number = number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringIntKey that = (StringIntKey) o;
            return (this.number == that.number && this.string.equals(that.string));
        }

        @Override
        public int hashCode() {
            return 63*number + string.hashCode();
        }

        @Override
        public String toString() {
            return String.format("(%s, %s)", string, number);
        }
    }

    static class TissueKey {
        String donorName;
        String mediumName;
        String fixativeName;
        String tissueTypeName;
        int spatialLocation;
        int replicate;

        public TissueKey(String donorName, String mediumName, String fixativeName, String tissueTypeName,
                         int spatialLocation, int replicate) {
            this.donorName = uc(donorName);
            this.mediumName = uc(mediumName);
            this.tissueTypeName = uc(tissueTypeName);
            this.fixativeName = uc(fixativeName);
            this.spatialLocation = spatialLocation;
            this.replicate = replicate;
        }

        public TissueKey(BlockRegisterRequest br) {
            this(br.getDonorIdentifier(), br.getMedium(), br.getFixative(), br.getTissueType(),
                    br.getSpatialLocation(), br.getReplicateNumber());
        }

        private static String uc(String value) {
            return (value==null || value.isEmpty() ? null : value.toUpperCase());
        }

        public boolean isComplete() {
            return (this.donorName!=null && this.tissueTypeName!=null
                    && this.mediumName!=null && this.fixativeName!=null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TissueKey that = (TissueKey) o;
            return (this.spatialLocation == that.spatialLocation
                    && this.replicate == that.replicate
                    && Objects.equals(this.donorName, that.donorName)
                    && Objects.equals(this.mediumName, that.mediumName)
                    && Objects.equals(this.fixativeName, that.fixativeName)
                    && Objects.equals(this.tissueTypeName, that.tissueTypeName));
        }

        @Override
        public int hashCode() {
            return Objects.hash(donorName, mediumName, fixativeName,tissueTypeName, spatialLocation, replicate);
        }

        @Override
        public String toString() {
            return String.format("{donor=%s, medium=%s, fixative=%s, tissue type=%s, spatial location=%s, replicate=%s}",
                    donorName, mediumName, fixativeName, tissueTypeName, spatialLocation, replicate);
        }
    }
}
