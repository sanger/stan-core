package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;

import java.util.*;
import java.util.function.Function;

/**
 * @author dr6
 */
public class RegisterValidationImp implements RegisterValidation {
    private final RegisterRequest request;
    private final LinkedHashSet<String> problems;
    private final DonorRepo donorRepo;
    private final HmdmcRepo hmdmcRepo;
    private final TissueTypeRepo ttRepo;
    private final LabwareTypeRepo ltRepo;
    private final MouldSizeRepo mouldSizeRepo;
    private final MediumRepo mediumRepo;
    private final TissueRepo tissueRepo;

    private final Map<String, Donor> donorMap = new HashMap<>();
    private final Map<String, Hmdmc> hmdmcMap = new HashMap<>();
    private final Map<StringIntKey, SpatialLocation> spatialLocationMap = new HashMap<>();
    private final Map<String, LabwareType> labwareTypeMap = new HashMap<>();
    private final Map<String, MouldSize> mouldSizeMap = new HashMap<>();
    private final Map<String, Medium> mediumMap = new HashMap<>();

    public RegisterValidationImp(RegisterRequest request, DonorRepo donorRepo,
                                 HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo, LabwareTypeRepo ltRepo,
                                 MouldSizeRepo mouldSizeRepo, MediumRepo mediumRepo, TissueRepo tissueRepo) {
        this.request = request;
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
        this.mouldSizeRepo = mouldSizeRepo;
        this.mediumRepo = mediumRepo;
        this.tissueRepo = tissueRepo;
        this.problems = new LinkedHashSet<>();
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
        validateByName("Unknown labware types: ", "Missing labware type",
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
        // tissue (incl. rep number and highest section and lw barcode...?)
        Set<StringIntKey> keys = new LinkedHashSet<>();
        for (BlockRegisterRequest block : blocks()) {
            if (block.getReplicateNumber() < 0) {
                addProblem("Replicate number cannot be negative.");
            }
            if (block.getHighestSection() < 0) {
                addProblem("Highest section number cannot be negative.");
            }
            if (block.getExternalIdentifier()==null || block.getExternalIdentifier().isEmpty()) {
                addProblem("Missing external identifier.");
                continue;
            }
            if (!keys.add(new StringIntKey(block.getExternalIdentifier(), block.getReplicateNumber()))) {
                addProblem(String.format("Repeated external identifier and replicate number: %s, %s",
                        block.getTissueType(), block.getReplicateNumber()));
            }
            Optional<Tissue> tissueOpt = tissueRepo.findByExternalNameAndReplicate(
                    block.getExternalIdentifier(), block.getReplicateNumber());
            if (tissueOpt.isPresent()) {
                Tissue tissue = tissueOpt.get();
                addProblem(String.format("Tissue with external identifier %s, replicate number %s already exists.",
                        tissue.getExternalName(), tissue.getReplicate()));
            }
        }
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
        return this.donorMap.get(name.toUpperCase());
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
}
