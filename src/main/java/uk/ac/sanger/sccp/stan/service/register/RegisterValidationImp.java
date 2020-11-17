package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.*;
import java.util.function.Function;

/**
 * @author dr6
 */
public class RegisterValidationImp implements RegisterValidation {
    private final RegisterRequest request;
    private final DonorRepo donorRepo;
    private final HmdmcRepo hmdmcRepo;
    private final TissueTypeRepo ttRepo;
    private final LabwareTypeRepo ltRepo;
    private final MouldSizeRepo mouldSizeRepo;
    private final MediumRepo mediumRepo;
    private final TissueRepo tissueRepo;
    private final Validator<String> donorNameValidation;
    private final Validator<String> externalNameValidation;

    final Map<String, Donor> donorMap = new HashMap<>();
    final Map<String, Hmdmc> hmdmcMap = new HashMap<>();
    final Map<StringIntKey, SpatialLocation> spatialLocationMap = new HashMap<>();
    final Map<String, LabwareType> labwareTypeMap = new HashMap<>();
    final Map<String, MouldSize> mouldSizeMap = new HashMap<>();
    final Map<String, Medium> mediumMap = new HashMap<>();
    final LinkedHashSet<String> problems = new LinkedHashSet<>();

    public RegisterValidationImp(RegisterRequest request, DonorRepo donorRepo,
                                 HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo, LabwareTypeRepo ltRepo,
                                 MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo, TissueRepo tissueRepo,
                                 Validator<String> donorNameValidation, Validator<String> externalNameValidation) {
        this.request = request;
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.mediumRepo = mediumRepo;
        this.tissueRepo = tissueRepo;
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
        validateTissues();
        return problems;
    }

    public void validateDonors() {
        for (BlockRegisterRequest block : blocks()) {
            boolean skip = false;
            if (block.getDonorIdentifier()==null || block.getDonorIdentifier().isEmpty()) {
                skip = true;
                addProblem("Missing donor identifier.");
            } else if (donorNameValidation!=null) {
                donorNameValidation.validate(block.getDonorIdentifier(), this::addProblem);
            }
            if (block.getLifeStage()==null) {
                skip = true;
                addProblem("Missing life stage.");
            }
            if (skip) {
                continue;
            }
            String donorNameUc = block.getDonorIdentifier().toUpperCase();
            Donor donor = donorMap.get(donorNameUc);
            if (donor==null) {
                donor = new Donor(null, block.getDonorIdentifier(), block.getLifeStage());
                donorMap.put(donorNameUc, donor);
            } else {
                if (donor.getLifeStage()!=block.getLifeStage()) {
                    addProblem("Multiple different life stages specified for donor "+donor.getDonorName());
                }
            }
        }
        for (Map.Entry<String, Donor> entry : donorMap.entrySet()) {
            Optional<Donor> optDonor = donorRepo.findByDonorName(entry.getKey());
            if (optDonor.isEmpty()) {
                continue;
            }
            Donor realDonor = optDonor.get();
            if (realDonor.getLifeStage()!=entry.getValue().getLifeStage()) {
                addProblem("Wrong life stage given for existing donor "+realDonor.getDonorName());
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
            addProblem("Unknown tissue types: "+unknownTissueTypes);
        }
    }

    public void validateHmdmcs() {
        validateByName("Unknown HMDMCs: ", "Missing HMDMC.",
                BlockRegisterRequest::getHmdmc, hmdmcRepo::findByHmdmc, hmdmcMap);
    }

    public void validateLabwareTypes() {
        validateByName("Unknown labware types: ", "Missing labware type.",
                BlockRegisterRequest::getLabwareType, ltRepo::findByName, labwareTypeMap);
    }

    public void validateMouldSizes() {
        validateByName("Unknown mould sizes: ", null,
                BlockRegisterRequest::getMouldSize, mouldSizeRepo::findByName, mouldSizeMap);
    }

    public void validateMediums() {
        validateByName("Unknown mediums: ", null,
                BlockRegisterRequest::getMedium, mediumRepo::findByName, mediumMap);
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
                    addProblem("There is already tissue in the database with external identifier " + block.getExternalIdentifier());
                }
            }
            TissueKey tissueKey = new TissueKey(block);
            if (tissueKey.isComplete() && !tissueKeys.add(tissueKey)) {
                addProblem("Repeated combination of fields: "+tissueKey);
            } else if (anySimilarTissuesInDatabase(block.getDonorIdentifier(), block.getTissueType(), block.getSpatialLocation(),
                    block.getMedium(), block.getReplicateNumber())) {
                addProblem("There is already similar tissue in the database: "+tissueKey);
            }
        }
    }

    public boolean anySimilarTissuesInDatabase(String donorName, String tissueTypeName, int spatialLocationCode,
                                               String mediumName, int replicate) {
        Donor donor = getDonor(donorName);
        if (donor==null) {
            return false;
        }
        if (tissueTypeName==null || tissueTypeName.isEmpty()) {
            return false;
        }
        SpatialLocation sl = spatialLocationMap.get(new StringIntKey(tissueTypeName, spatialLocationCode));
        if (sl==null) {
            return false;
        }
        List<Tissue> tissues = tissueRepo.findByDonorIdAndSpatialLocationIdAndReplicate(donor.getId(), sl.getId(), replicate);
        if (tissues.isEmpty()) {
            return false;
        }
        if (mediumName==null || mediumName.isEmpty()) {
            return tissues.stream()
                    .anyMatch(tissue -> tissue.getMedium()==null);
        }
        Medium medium = getMedium(mediumName);
        if (medium==null) {
            // no such medium, problem reported elsewhere
            return false;
        }
        return tissues.stream()
                .anyMatch(tissue -> tissue.getMedium()!=null && medium.getId().equals(tissue.getMedium().getId()));
    }

    private <E> void validateByName(String unknownMessage, String missingMessage,
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
        if (missing && missingMessage!=null) {
            addProblem(missingMessage);
        }
        if (!unknownNames.isEmpty()) {
            addProblem(unknownMessage + unknownNames);
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
        return (name==null ? null : this.donorMap.get(name.toUpperCase()));
    }

    @Override
    public Hmdmc getHmdmc(String hmdmc) {
        return this.hmdmcMap.get(hmdmc.toUpperCase());
    }

    @Override
    public SpatialLocation getSpatialLocation(String tissueTypeName, int code) {
        return this.spatialLocationMap.get(new StringIntKey(tissueTypeName, code));
    }

    @Override
    public LabwareType getLabwareType(String name) {
        return this.labwareTypeMap.get(name.toUpperCase());
    }

    @Override
    public MouldSize getMouldSize(String name) {
        return (name==null ? null : this.mouldSizeMap.get(name.toUpperCase()));
    }

    @Override
    public Medium getMedium(String name) {
        return (name==null ? null : this.mediumMap.get(name.toUpperCase()));
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
        String tissueTypeName;
        int spatialLocation;
        int replicate;

        public TissueKey(String donorName, String mediumName, String tissueTypeName, int spatialLocation, int replicate) {
            this.donorName = uc(donorName);
            this.mediumName = uc(mediumName);
            this.tissueTypeName = uc(tissueTypeName);
            this.spatialLocation = spatialLocation;
            this.replicate = replicate;
        }

        public TissueKey(BlockRegisterRequest br) {
            this(br.getDonorIdentifier(), br.getMedium(), br.getTissueType(), br.getSpatialLocation(), br.getReplicateNumber());
        }

        private static String uc(String value) {
            if (value==null || value.isEmpty()) {
                return null;
            }
            return value.toUpperCase();
        }

        public boolean isComplete() {
            return (this.donorName!=null && this.tissueTypeName!=null);
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
                    && Objects.equals(this.tissueTypeName, that.tissueTypeName));
        }

        @Override
        public int hashCode() {
            return Objects.hash(donorName, mediumName, tissueTypeName, spatialLocation, replicate);
        }

        @Override
        public String toString() {
            return String.format("{donor=%s, medium=%s, tissue type=%s, spatial location=%s, replicate=%s}",
                    donorName, mediumName, tissueTypeName, spatialLocation, replicate);
        }
    }
}
