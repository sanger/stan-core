package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.BioRiskService;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
public class BlockRegisterValidationImp implements RegisterValidation {
    private final BlockRegisterRequest request;
    private final DonorRepo donorRepo;
    private final HmdmcRepo hmdmcRepo;
    private final TissueTypeRepo ttRepo;
    private final LabwareTypeRepo ltRepo;
    private final MediumRepo mediumRepo;
    private final FixativeRepo fixativeRepo;
    private final TissueRepo tissueRepo;
    private final SpeciesRepo speciesRepo;
    private final CellClassRepo cellClassRepo;
    private final LabwareRepo lwRepo;
    private final Validator<String> donorNameValidation;
    private final Validator<String> externalNameValidation;
    private final Validator<String> replicateValidator;
    private final Validator<String> externalBarcodeValidator;
    private final BlockFieldChecker blockFieldChecker;
    private final BioRiskService bioRiskService;
    private final WorkService workService;

    final UCMap<Donor> donorMap = new UCMap<>();
    final UCMap<Tissue> tissueMap = new UCMap<>();
    final UCMap<Hmdmc> hmdmcMap = new UCMap<>();
    final UCMap<Species> speciesMap = new UCMap<>();
    final Map<StringIntKey, SpatialLocation> spatialLocationMap = new HashMap<>();
    final UCMap<LabwareType> labwareTypeMap = new UCMap<>();
    final UCMap<Medium> mediumMap = new UCMap<>();
    final UCMap<Fixative> fixativeMap = new UCMap<>();
    UCMap<CellClass> cellClassMap;
    UCMap<BioRisk> bioRiskMap;
    Collection<Work> works;
    final LinkedHashSet<String> problems = new LinkedHashSet<>();

    public BlockRegisterValidationImp(BlockRegisterRequest request, DonorRepo donorRepo,
                                      HmdmcRepo hmdmcRepo, TissueTypeRepo ttRepo, LabwareTypeRepo ltRepo,
                                      MediumRepo mediumRepo, FixativeRepo fixativeRepo, TissueRepo tissueRepo,
                                      SpeciesRepo speciesRepo, CellClassRepo cellClassRepo, LabwareRepo lwRepo,
                                      Validator<String> donorNameValidation, Validator<String> externalNameValidation,
                                      Validator<String> replicateValidator, Validator<String> externalBarcodeValidator,
                                      BlockFieldChecker blockFieldChecker,
                                      BioRiskService bioRiskService, WorkService workService) {
        this.request = request;
        this.donorRepo = donorRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.ttRepo = ttRepo;
        this.ltRepo = ltRepo;
        this.mediumRepo = mediumRepo;
        this.fixativeRepo = fixativeRepo;
        this.tissueRepo = tissueRepo;
        this.speciesRepo = speciesRepo;
        this.cellClassRepo = cellClassRepo;
        this.lwRepo = lwRepo;
        this.donorNameValidation = donorNameValidation;
        this.externalNameValidation = externalNameValidation;
        this.replicateValidator = replicateValidator;
        this.externalBarcodeValidator = externalBarcodeValidator;
        this.blockFieldChecker = blockFieldChecker;
        this.bioRiskService = bioRiskService;
        this.workService = workService;
    }

    @Override
    public Collection<String> validate() {
        if (request.getLabware().stream().allMatch(blw -> blw.getSamples().isEmpty())) {
            problems.add("No labware specified in request.");
            return problems;
        }
        validateDonors();
        validateHmdmcs();
        validateSpatialLocations();
        validateLabwareTypes();
        validateExternalBarcodes();
        validateAddresses();
        validateMediums();
        validateFixatives();
        validateCollectionDates();
        validateExistingTissues();
        validateNewTissues();
        validateBioRisks();
        validateWorks();
        validateCellClasses();
        return problems;
    }

    @Override
    public Donor getDonor(String name) {
        return donorMap.get(name);
    }

    @Override
    public Hmdmc getHmdmc(String hmdmc) {
        return hmdmcMap.get(hmdmc);
    }

    @Override
    public SpatialLocation getSpatialLocation(String tissueTypeName, int code) {
        if (tissueTypeName==null) {
            return null;
        }
        return spatialLocationMap.get(new StringIntKey(tissueTypeName, code));
    }

    @Override
    public LabwareType getLabwareType(String name) {
        return labwareTypeMap.get(name);
    }

    @Override
    public Medium getMedium(String name) {
        return mediumMap.get(name);
    }

    @Override
    public Fixative getFixative(String name) {
        return fixativeMap.get(name);
    }

    @Override
    public Tissue getTissue(String externalName) {
        return tissueMap.get(externalName);
    }

    @Override
    public BioRisk getBioRisk(String code) {
        return bioRiskMap.get(code);
    }

    @Override
    public CellClass getCellClass(String name) {
        return cellClassMap.get(name);
    }

    @Override
    public Collection<Work> getWorks() {
        return works;
    }

    public void validateDonors() {
        for (BlockRegisterSample brs : iter(blockSamples())) {
            boolean skip = false;
            Species species = null;
            if (nullOrEmpty(brs.getDonorIdentifier())) {
                skip = true;
                addProblem("Missing donor identifier.");
            } else if (donorNameValidation!=null) {
                donorNameValidation.validate(brs.getDonorIdentifier(), this::addProblem);
            }
            if (nullOrEmpty(brs.getSpecies())) {
                addProblem("Missing species.");
            } else {
                species = speciesMap.get(brs.getSpecies());
                if (species==null && !speciesMap.containsKey(brs.getSpecies())) {
                    species = speciesRepo.findByName(brs.getSpecies()).orElse(null);
                    speciesMap.put(brs.getSpecies(), species);
                    if (species==null) {
                        addProblem("Unknown species: "+repr(brs.getSpecies()));
                    } else if (!species.isEnabled()) {
                        addProblem("Species is not enabled: "+species.getName());
                    }
                }
            }
            if (skip) {
                continue;
            }
            Donor donor = donorMap.get(brs.getDonorIdentifier());
            if (donor==null) {
                donor = new Donor(null, brs.getDonorIdentifier(), brs.getLifeStage(), species);
                donorMap.put(brs.getDonorIdentifier(), donor);
            } else {
                if (donor.getLifeStage()!= brs.getLifeStage()) {
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

    public void validateHmdmcs() {
        Set<String> unknownHmdmcs = new LinkedHashSet<>();
        boolean unwanted = false;
        boolean missing = false;
        for (BlockRegisterSample brs : iter(blockSamples())) {
            boolean needsHmdmc = false;
            boolean needsNoHmdmc = false;
            if (brs.getSpecies()!=null && !brs.getSpecies().isEmpty()) {
                needsHmdmc = Species.isHumanName(brs.getSpecies());
                needsNoHmdmc = !needsHmdmc;
            }
            String hmdmcString = brs.getHmdmc();
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

            if (hmdmcMap.containsKey(hmdmcString)) {
                continue;
            }
            Hmdmc hmdmc = hmdmcRepo.findByHmdmc(hmdmcString).orElse(null);
            hmdmcMap.put(hmdmcString, hmdmc);
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

    public void validateSpatialLocations() {
        UCMap<TissueType> tissueTypeMap = new UCMap<>();
        Set<String> unknownTissueTypes = new LinkedHashSet<>();
        for (BlockRegisterSample block : iter(blockSamples())) {
            if (nullOrEmpty(block.getTissueType())) {
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
                if (!tt.isEnabled()) {
                    addProblem(String.format("Tissue type \"%s\" is disabled.", tt.getName()));
                }
            }
            final int slCode = block.getSpatialLocation();
            Optional<SpatialLocation> slOpt = tt.getSpatialLocations().stream()
                    .filter(spl -> spl.getCode()==slCode)
                    .findAny();
            if (slOpt.isEmpty()) {
                addProblem(String.format("Unknown spatial location %s for tissue type %s.", slCode, tt.getName()));
                continue;
            }
            SpatialLocation sl = slOpt.get();
            if (tt.isEnabled() && !sl.isEnabled()) {
                addProblem(String.format("Spatial location is disabled: %s for tissue type %s.", sl.getCode(), tt.getName()));
            }
            spatialLocationMap.put(key, sl);
        }
        if (!unknownTissueTypes.isEmpty()) {
            if (unknownTissueTypes.size()==1) {
                addProblem("Unknown tissue type: "+unknownTissueTypes);
            } else {
                addProblem("Unknown tissue types: " + unknownTissueTypes);
            }
        }
    }

    public void validateLabwareTypes() {
        validateByName("labware type", BlockRegisterLabware::getLabwareType, ltRepo::findByName, labwareTypeMap);
    }

    public void validateMediums() {
        validateByName("medium", BlockRegisterLabware::getMedium, mediumRepo::findByName, mediumMap);
    }

    public void validateFixatives() {
        validateByName("fixative", BlockRegisterLabware::getFixative, fixativeRepo::findByName, fixativeMap);
    }

    public void validateCollectionDates() {
        boolean missing = false;
        LocalDate today = LocalDate.now();
        Set<LocalDate> badDates = new LinkedHashSet<>();
        UCMap<LocalDate> extToDate = new UCMap<>();
        for (BlockRegisterSample brs : iter(blockSamples())) {
            if (brs.getSampleCollectionDate()==null) {
                if (brs.getLifeStage()==LifeStage.fetal && brs.getSpecies()!=null
                        && Species.isHumanName(brs.getSpecies())) {
                    missing = true;
                }
            } else if (brs.getSampleCollectionDate().isAfter(today)) {
                badDates.add(brs.getSampleCollectionDate());
            }
            if (brs.getExternalIdentifier()!=null && !brs.getExternalIdentifier().isEmpty()) {
                String key = brs.getExternalIdentifier().trim().toUpperCase();
                if (!key.isEmpty()) {
                    if (extToDate.containsKey(key) && !Objects.equals(extToDate.get(key), brs.getSampleCollectionDate())) {
                        addProblem("Inconsistent collection dates specified for tissue " + key + ".");
                    } else {
                        extToDate.put(key, brs.getSampleCollectionDate());
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
        List<BlockRegisterLabwareAndSample> blocksForExistingTissues = new ArrayList<>();
        for (BlockRegisterLabware blw : request.getLabware()) {
            for (BlockRegisterSample brs : blw.getSamples()) {
                if (brs.isExistingTissue()) {
                    blocksForExistingTissues.add(new BlockRegisterLabwareAndSample(blw, brs));
                }
            }
        }
        if (blocksForExistingTissues.isEmpty()) {
            return;
        }
        if (blocksForExistingTissues.stream().anyMatch(brs -> nullOrEmpty(brs.sample().getExternalIdentifier()))) {
            addProblem("Missing external identifier.");
        }
        Set<String> xns = blocksForExistingTissues.stream()
                .map(b -> b.sample().getExternalIdentifier())
                .filter(xn -> !nullOrEmpty(xn))
                .collect(toLinkedHashSet());
        if (xns.isEmpty()) {
            return;
        }
        tissueRepo.findAllByExternalNameIn(xns).forEach(t -> tissueMap.put(t.getExternalName(), t));

        Set<String> missing = xns.stream()
                .filter(xn -> !tissueMap.containsKey(xn))
                .collect(toLinkedHashSet());
        if (!missing.isEmpty()) {
            addProblem("Existing external identifiers not recognised: " + reprCollection(missing));
        }

        for (BlockRegisterLabwareAndSample b : blocksForExistingTissues) {
            String xn = b.sample().getExternalIdentifier();
            if (!nullOrEmpty(xn)) {
                Tissue tissue = tissueMap.get(xn.toUpperCase());
                if (tissue != null) {
                    blockFieldChecker.check(this::addProblem, b.labware(), b.sample(), tissue);
                }
            }
        }
    }

    public void validateNewTissues() {
        // NB repeated new external identifier in one request is still disallowed
        Set<String> externalNames = new HashSet<>();
        for (BlockRegisterSample brs : iter(blockSamples())) {
            if (brs.isExistingTissue()) {
                continue;
            }
            if (nullOrEmpty(brs.getReplicateNumber())) {
                addProblem("Missing replicate number.");
            } else {
                replicateValidator.validate(brs.getReplicateNumber(), this::addProblem);
            }
            if (brs.getHighestSection() < 0) {
                addProblem("Highest section number cannot be negative.");
            }
            if (nullOrEmpty(brs.getExternalIdentifier())) {
                addProblem("Missing external identifier.");
            } else {
                if (externalNameValidation != null) {
                    externalNameValidation.validate(brs.getExternalIdentifier(), this::addProblem);
                }
                if (!externalNames.add(brs.getExternalIdentifier().toUpperCase())) {
                    addProblem("Repeated external identifier: " + brs.getExternalIdentifier());
                } else if (!tissueRepo.findAllByExternalName(brs.getExternalIdentifier()).isEmpty()) {
                    addProblem(String.format("There is already tissue in the database with external identifier %s.",
                            brs.getExternalIdentifier()));
                }
            }
        }
    }

    public void validateBioRisks() {
        this.bioRiskMap = bioRiskService.loadAndValidateBioRisks(problems, blockSamples(),
                BlockRegisterSample::getBioRiskCode, BlockRegisterSample::setBioRiskCode);
    }

    public void validateCellClasses() {
        Set<String> cellClassNames = new HashSet<>();
        boolean anyMissing = false;
        for (BlockRegisterSample brs : iter(blockSamples())) {
            String cellClassName = brs.getCellClass();
            if (nullOrEmpty(cellClassName)) {
                anyMissing = true;
            } else {
                cellClassNames.add(cellClassName);
            }
        }
        if (anyMissing) {
            addProblem("Missing cell class name.");
        }
        if (cellClassNames.isEmpty()) {
            cellClassMap = new UCMap<>(0);
            return;
        }
        cellClassMap = cellClassRepo.findMapByNameIn(cellClassNames);
        List<String> missing = cellClassNames.stream()
                .filter(name -> cellClassMap.get(name) == null)
                .map(BasicUtils::repr)
                .toList();
        if (!missing.isEmpty()) {
            problems.add("Unknown cell class name: " + missing);
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

    public void validateExternalBarcodes() {
        Set<String> barcodes = new HashSet<>();
        boolean anyMissing = false;
        for (BlockRegisterLabware brl : request.getLabware()) {
            String barcode = brl.getExternalBarcode();
            if (nullOrEmpty(barcode)) {
                anyMissing = true;
            } else if (!barcodes.add(barcode.toUpperCase())) {
                addProblem("External barcode given multiple times: " + barcode);
            }
        }
        if (anyMissing) {
            problems.add("Missing external barcode.");
        }
        for (String barcode : barcodes) {
            externalBarcodeValidator.validate(barcode, this::addProblem);
        }
        Set<String> usedBarcodes = lwRepo.findBarcodesByBarcodeIn(barcodes);
        if (!usedBarcodes.isEmpty()) {
            addProblem("Labware barcode already in use: " + usedBarcodes);
        }
        barcodes.removeAll(usedBarcodes);
        Set<String> usedExternalBarcodes = lwRepo.findExternalBarcodesIn(barcodes);
        if (!usedExternalBarcodes.isEmpty()) {
            addProblem("External barcode already in use: " + usedExternalBarcodes);
        }
    }

    public void validateAddresses() {
        Map<LabwareType, Set<Address>> lwTypeInvalidAddresses = new HashMap<>();
        boolean missing = false;
        for (BlockRegisterLabware brl : request.getLabware()) {
            LabwareType lt = labwareTypeMap.get(brl.getLabwareType());
            if (lt == null) {
                continue;
            }
            for (BlockRegisterSample brs : brl.getSamples()) {
                if (brs.getAddresses().isEmpty()) {
                    missing = true;
                } else {
                    for (Address ad : brs.getAddresses()) {
                        if (lt.indexOf(ad) < 0) {
                            lwTypeInvalidAddresses.computeIfAbsent(lt, k -> new HashSet<>()).add(ad);
                        }
                    }
                }
            }
        }
        if (missing) {
            problems.add("Slot addresses missing from request.");
        }
        if (!lwTypeInvalidAddresses.isEmpty()) {
            lwTypeInvalidAddresses.forEach((lt, invalidAddresses)
                    -> problems.add(String.format("Invalid slot addresses for labware type %s: %s", lt.getName(), invalidAddresses)));
        }
    }


    <E> void validateByName(String entityName,
                            Function<BlockRegisterLabware, String> nameFunction,
                            Function<String, Optional<E>> lkp,
                            UCMap<E> map) {
        Set<String> unknownNames = new LinkedHashSet<>();
        boolean missing = false;
        for (BlockRegisterLabware brl : request.getLabware()) {
            String name = nameFunction.apply(brl);
            if (nullOrEmpty(name)) {
                missing = true;
                continue;
            }
            if (unknownNames.contains(name)) {
                continue;
            }
            if (map.containsKey(name)) {
                continue;
            }
            Optional<E> opt = lkp.apply(name);
            if (opt.isEmpty()) {
                unknownNames.add(repr(name));
                continue;
            }
            map.put(name, opt.get());
        }
        if (missing) {
            addProblem(String.format("Missing %s.", entityName));
        }
        if (!unknownNames.isEmpty()) {
            addProblem(String.format("Unknown %s%s: %s", entityName, unknownNames.size()==1 ? "" : "s", unknownNames));
        }
    }

    boolean addProblem(String problem) {
        return problems.add(problem);
    }

    Stream<BlockRegisterSample> blockSamples() {
        return this.request.getLabware().stream().flatMap(brl -> brl.getSamples().stream());
    }

    record StringIntKey(String string, int number) {
        StringIntKey(String string, int number) {
            this.string = string.toUpperCase();
            this.number = number;
        }
    }

    record BlockRegisterLabwareAndSample(BlockRegisterLabware labware, BlockRegisterSample sample) {}
}
