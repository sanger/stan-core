package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;
import static uk.ac.sanger.sccp.utils.UCMap.toUCMap;

/**
 * Validation helper for section registration.
 * @author dr6
 */
public class SectionRegisterValidation {
    private final Set<String> problems = new LinkedHashSet<>();
    private final SectionRegisterRequest request;

    private final DonorRepo donorRepo;
    private final SpeciesRepo speciesRepo;
    private final LabwareTypeRepo lwTypeRepo;
    private final LabwareRepo lwRepo;
    private final HmdmcRepo hmdmcRepo;
    private final TissueTypeRepo tissueTypeRepo;
    private final FixativeRepo fixativeRepo;
    private final MediumRepo mediumRepo;
    private final TissueRepo tissueRepo;
    private final BioStateRepo bioStateRepo;
    private final Validator<String> externalBarcodeValidation;
    private final Validator<String> donorNameValidation;
    private final Validator<String> externalNameValidation;
    private final Validator<String> visiumLpBarcodeValidation;
    private final Validator<String> replicateValidator;

    public SectionRegisterValidation(SectionRegisterRequest request,
                                     DonorRepo donorRepo, SpeciesRepo speciesRepo, LabwareTypeRepo lwTypeRepo,
                                     LabwareRepo lwRepo, HmdmcRepo hmdmcRepo,
                                     TissueTypeRepo tissueTypeRepo, FixativeRepo fixativeRepo, MediumRepo mediumRepo,
                                     TissueRepo tissueRepo, BioStateRepo bioStateRepo,
                                     Validator<String> externalBarcodeValidation, Validator<String> donorNameValidation,
                                     Validator<String> externalNameValidation, Validator<String> replicateValidator,
                                     Validator<String> visiumLpBarcodeValidation) {
        this.request = request;
        this.donorRepo = donorRepo;
        this.speciesRepo = speciesRepo;
        this.lwTypeRepo = lwTypeRepo;
        this.lwRepo = lwRepo;
        this.hmdmcRepo = hmdmcRepo;
        this.tissueTypeRepo = tissueTypeRepo;
        this.fixativeRepo = fixativeRepo;
        this.mediumRepo = mediumRepo;
        this.tissueRepo = tissueRepo;
        this.bioStateRepo = bioStateRepo;
        this.externalBarcodeValidation = externalBarcodeValidation;
        this.donorNameValidation = donorNameValidation;
        this.externalNameValidation = externalNameValidation;
        this.replicateValidator = replicateValidator;
        this.visiumLpBarcodeValidation = visiumLpBarcodeValidation;
    }

    public ValidatedSections validate() {
        checkEmpty();
        UCMap<Donor> donors = validateDonors();
        UCMap<LabwareType> lwTypes = validateLabwareTypes();
        validateBarcodes();
        UCMap<Tissue> tissues = validateTissues(donors);
        UCMap<Sample> samples = validateSamples(tissues);
        if (!problems.isEmpty()) {
            return null;
        }
        return new ValidatedSections(lwTypes, donors, samples);
    }

    public void checkEmpty() {
        if (request.getLabware().isEmpty()) {
            addProblem("No labware specified in request.");
        } else if (request.getLabware().stream().anyMatch(lw -> lw.getContents()==null || lw.getContents().isEmpty())) {
            addProblem("Labware requested without contents.");
        }
    }

    /**
     * Checks donors, life stages and species.
     * Creates a map of donor name (upper case) to donor.
     * The donors in the map are the ones loaded from the database; and where they don't exist yet,
     * they are new unpersisted donor object.
     * Problems are added to the internal problem collection.
     * @return a map of donor name (upper case) to donors.
     */
    public UCMap<Donor> validateDonors() {
        UCMap<Donor> donorMap = loadAllFromSectionsToStringMap(request, SectionRegisterContent::getDonorIdentifier,
                Donor::getDonorName, donorRepo::findAllByDonorNameIn);
        UCMap<Species> speciesMap = loadAllFromSectionsToStringMap(request, SectionRegisterContent::getSpecies,
                Species::getName, speciesRepo::findAllByNameIn);

        checkEntitiesEnabled(speciesMap.values(), "Species", Species::getName);

        for (SectionRegisterContent content : contents()) {
            boolean skip = false;
            Species species = null;
            if (content.getDonorIdentifier()==null || content.getDonorIdentifier().isEmpty()) {
                skip = true;
                addProblem("Missing donor identifier.");
            } else if (donorNameValidation!=null) {
                donorNameValidation.validate(content.getDonorIdentifier(), this::addProblem);
            }
            if (content.getLifeStage()==null) {
                addProblem("Missing life stage.");
            }
            if (content.getSpecies()==null || content.getSpecies().isEmpty()) {
                addProblem("Missing species.");
            } else {
                species = speciesMap.get(content.getSpecies());
                if (species==null) {
                    addProblem("Unknown species: "+repr(content.getSpecies()));
                }
            }
            if (skip) {
                continue;
            }
            String donorName = content.getDonorIdentifier();
            Donor donor = donorMap.get(donorName);
            if (donor==null) {
                donor = new Donor(null, content.getDonorIdentifier(), content.getLifeStage(), species);
                donorMap.put(donorName, donor);
            } else {
                if (content.getLifeStage()!=null && content.getLifeStage()!=donor.getLifeStage()) {
                    if (donor.getId()!=null) {
                        addProblem("Wrong life stage given for existing donor "+donor.getDonorName());
                    } else if (donor.getLifeStage()!=null) {
                        addProblem("Multiple different life stages specified for donor "+donor.getDonorName());
                    } else {
                        donor.setLifeStage(content.getLifeStage());
                    }
                }
                if (species!=null && !species.equals(donor.getSpecies())) {
                    if (donor.getId()!=null) {
                        addProblem("Wrong species given for existing donor "+donor.getDonorName());
                    } else if (donor.getSpecies()!=null) {
                        addProblem("Multiple different species specified for donor "+donor.getDonorName());
                    } else {
                        donor.setSpecies(species);
                    }
                }
            }
        }
        return donorMap;
    }

    /**
     * Checks labware types and addresses.
     */
    public UCMap<LabwareType> validateLabwareTypes() {
        Set<String> lwTypeNames = request.getLabware().stream()
                .map(SectionRegisterLabware::getLabwareType)
                .filter(s -> s!=null && !s.isEmpty())
                .collect(toSet());
        UCMap<LabwareType> lwTypeMap = lwTypeRepo.findAllByNameIn(lwTypeNames).stream()
                .collect(toUCMap(LabwareType::getName));

        for (var lw : request.getLabware()) {
            final String ltName = lw.getLabwareType();
            if (ltName==null || ltName.isEmpty()) {
                addProblem("Missing labware type.");
                continue;
            }
            LabwareType lt = lwTypeMap.get(ltName);
            if (lt==null) {
                addProblem("Unknown labware type "+repr(ltName));
                continue;
            }
            for (var content : lw.getContents()) {
                Address address = content.getAddress();
                if (address==null) {
                    addProblem("Missing slot address.");
                } else if (lt.indexOf(address) < 0) {
                    addProblem("Invalid address %s in labware type %s.", address, lt.getName());
                }
            }
        }
        // Allow duplicate addresses
        return lwTypeMap;
    }

    public void validateBarcodes() {
        final Map<String, Set<String>> bcProblemMap = new HashMap<>();
        Set<String> seenBarcodes = new LinkedHashSet<>();
        boolean missing = false;
        BiConsumer<String, String> bcProblem = (problem, bc) ->
                bcProblemMap.computeIfAbsent(problem, k -> new LinkedHashSet<>()).add(bc);
        for (var lw : request.getLabware()) {
            String bc = lw.getExternalBarcode();
            if (bc==null || bc.isEmpty()) {
                missing = true;
                continue;
            }
            String bcUpper = bc.toUpperCase();
            if (!seenBarcodes.add(bcUpper)) {
                bcProblem.accept("Repeated barcode{s}", bc);
                continue;
            }
            if (bcUpper.startsWith("STAN-") || bcUpper.startsWith("STO-")) {
                bcProblem.accept("Invalid external barcode prefix", bc);
                continue;
            }
            boolean isVisiumLp = (lw.getLabwareType()!=null && lw.getLabwareType().equalsIgnoreCase("Visium LP"));
            (isVisiumLp ? visiumLpBarcodeValidation : externalBarcodeValidation).validate(bc, this::addProblem);
            if (lwRepo.existsByExternalBarcode(bc)) {
                bcProblem.accept("External barcode{s} already used", bc);
            } else if (lwRepo.existsByBarcode(bc)) {
                bcProblem.accept("Labware barcode{s} already used", bc);
            }
        }
        if (missing) {
            addProblem("Missing external barcode.");
        }
        for (var entry : bcProblemMap.entrySet()) {
            String problem = entry.getKey();
            Set<String> bcs = entry.getValue();
            addProblem(pluralise(problem, bcs.size())+": "+bcs);
        }
    }

    // Yikes, this method is big.
    public UCMap<Tissue> validateTissues(UCMap<Donor> donorMap) {
        UCMap<Hmdmc> hmdmcMap = loadAllFromSectionsToStringMap(request, SectionRegisterContent::getHmdmc,
                Hmdmc::getHmdmc, hmdmcRepo::findAllByHmdmcIn);
        checkEntitiesEnabled(hmdmcMap.values(), "HuMFre number", Hmdmc::getHmdmc);
        UCMap<TissueType> tissueTypeMap = loadAllFromSectionsToStringMap(request, SectionRegisterContent::getTissueType,
                TissueType::getName, tissueTypeRepo::findAllByNameIn);
        UCMap<Fixative> fixativeMap = loadAllFromSectionsToStringMap(request, SectionRegisterContent::getFixative,
                Fixative::getName, fixativeRepo::findAllByNameIn);
        UCMap<Medium> mediumMap = loadAllFromSectionsToStringMap(request, SectionRegisterContent::getMedium,
                Medium::getName, mediumRepo::findAllByNameIn);
        UCMap<Tissue> existingTissueMap = loadAllFromSectionsToStringMap(request, SectionRegisterContent::getExternalIdentifier,
                Tissue::getExternalName, tissueRepo::findAllByExternalNameIn);

        final Map<String, Set<String>> problemMap = new HashMap<>();
        BiConsumer<String, String> problemFn = (problem, bc) ->
                problemMap.computeIfAbsent(problem, k -> new LinkedHashSet<>()).add(bc);

        UCMap<Tissue> tissueMap = new UCMap<>();
        final Set<String> seenExternalNames = new HashSet<>();
        for (var content : contents()) {
            String hmdmcString = content.getHmdmc();
            boolean needHmdmc, needNoHmdmc;
            if (content.getSpecies()==null || content.getSpecies().isEmpty()) {
                needHmdmc = needNoHmdmc = false;
            } else {
                needNoHmdmc = !(needHmdmc = content.getSpecies().equalsIgnoreCase("Human"));
            }
            Hmdmc hmdmc;
            if (hmdmcString==null || hmdmcString.isEmpty()) {
                if (needHmdmc) {
                    addProblem("Missing HuMFre number.");
                }
                hmdmc = null;
            } else {
                if (needNoHmdmc) {
                    addProblem("Unexpected HuMFre number received for non-human tissue.");
                }
                hmdmc = hmdmcMap.get(hmdmcString);
                if (hmdmc==null) {
                    problemFn.accept("Unknown HuMFre number{s}", hmdmcString);
                }
            }

            String externalIdentifier = content.getExternalIdentifier();
            if (externalIdentifier==null || externalIdentifier.isEmpty()) {
                addProblem("Missing external identifier.");
            } else if (!seenExternalNames.add(externalIdentifier.toUpperCase())) {
                problemFn.accept("Repeated external identifier{s}", externalIdentifier);
            } else {
                externalNameValidation.validate(externalIdentifier, this::addProblem);
                if (existingTissueMap.get(externalIdentifier)!=null) {
                    problemFn.accept("External identifier{s} already in use", externalIdentifier);
                }
            }

            TissueType tissueType = loadItem(content.getTissueType(), tissueTypeMap, "Missing tissue type.",
                    "Unknown tissue type{s}", problemFn);

            Integer slCode = content.getSpatialLocation();
            SpatialLocation spatialLocation = null;
            if (slCode==null) {
                addProblem("Missing spatial location.");
            } else if (tissueType!=null) {
                spatialLocation = tissueType.getSpatialLocations().stream()
                        .filter(sl -> slCode.equals(sl.getCode()))
                        .findAny()
                        .orElse(null);
                if (spatialLocation==null) {
                    problemFn.accept("Unknown spatial location{s}", slCode+" for "+tissueType.getName());
                }
            }

            if (content.getReplicateNumber()==null || content.getReplicateNumber().isEmpty()) {
                addProblem("Missing replicate number.");
            } else {
                replicateValidator.validate(content.getReplicateNumber(), this::addProblem);
            }

            Fixative fixative = loadItem(content.getFixative(), fixativeMap, "Missing fixative.",
                    "Unknown fixative{s}", problemFn);

            Medium medium = loadItem(content.getMedium(), mediumMap, "Missing medium.",
                    "Unknown medium{s}", problemFn);

            String donorName = content.getDonorIdentifier();
            Donor donor;
            if (donorName==null || donorName.isEmpty()) {
                donor = null;
            } else {
                donor = donorMap.get(content.getDonorIdentifier());
            }

            if (donor==null || externalIdentifier==null || !problemMap.isEmpty() || !problems.isEmpty()) {
                continue;
            }

            Tissue tissue = new Tissue(null, externalIdentifier, content.getReplicateNumber().toLowerCase(),
                    spatialLocation, donor, medium, fixative, hmdmc);
            tissueMap.put(externalIdentifier, tissue);
        }

        for (var entry : problemMap.entrySet()) {
            String problem = entry.getKey();
            var strings = entry.getValue();
            addProblem(pluralise(problem, strings.size())+": "+strings);
        }
        return tissueMap;
    }

    private <E> E loadItem(String string, UCMap<E> itemMap, String missingMsg, String unknownMsg,
                           BiConsumer<String, String> problemFn) {
        if (string==null || string.isEmpty()) {
            addProblem(missingMsg);
            return null;
        }
        E item = itemMap.get(string);
        if (item==null) {
            problemFn.accept(unknownMsg, string);
        }
        return item;
    }

    public UCMap<Sample> validateSamples(UCMap<Tissue> tissueMap) {
        UCMap<Sample> sampleMap = new UCMap<>();
        Optional<BioState> bsOpt = bioStateRepo.findByName("Tissue");
        if (bsOpt.isEmpty()) {
            addProblem("Bio state \"Tissue\" not found.");
        }
        BioState bs = bsOpt.orElse(null);
        for (var content : contents()) {
            if (content.getSectionNumber()==null) {
                addProblem("Missing section number.");
            } else if (content.getSectionNumber() < 0) {
                addProblem("Section number cannot be negative.");
            }
            if (content.getSectionThickness()!=null) {
                if (content.getSectionThickness()==0) {
                    addProblem("Section thickness cannot be zero.");
                } else if (content.getSectionThickness() < 0) {
                    addProblem("Section thickness cannot be negative.");
                }
            }
            if (!problems.isEmpty()) {
                continue;
            }
            String externalName = content.getExternalIdentifier();
            if (externalName==null || externalName.isEmpty()) {
                continue;
            }
            Tissue tissue = tissueMap.get(externalName);
            if (tissue!=null) {
                sampleMap.put(externalName, new Sample(null, content.getSectionNumber(), tissue, bs));
            }
        }
        return sampleMap;
    }

    private Stream<SectionRegisterContent> contentStream() {
        return request.getLabware().stream().flatMap(lw -> lw.getContents().stream());
    }

    private Iterable<SectionRegisterContent> contents() {
        return () -> contentStream().iterator();
    }

    private void addProblem(String problem) {
        this.problems.add(problem);
    }

    private void addProblem(String format, Object... params) {
        addProblem(String.format(format, params));
    }

    public Set<String> getProblems() {
        return this.problems;
    }

    public void throwError() throws ValidationException {
        if (!problems.isEmpty()) {
            throw new ValidationException("The section register request could not be validated.", problems);
        }
    }

    private <E> UCMap<E> loadAllFromSectionsToStringMap(SectionRegisterRequest request,
                                                              Function<SectionRegisterContent, String> requestFunction,
                                                              Function<? super E, String> entityFunction,
                                                              Function<? super Set<String>, ? extends Collection<E>> lookupFunction) {
        Set<String> strings = request.getLabware().stream()
                .flatMap(lw -> lw.getContents().stream().map(requestFunction))
                .filter(s -> s!=null && !s.isEmpty())
                .collect(toSet());
        if (strings.isEmpty()) {
            return new UCMap<>(0);
        }
        return lookupFunction.apply(strings).stream().collect(toUCMap(entityFunction));
    }

    private <E extends HasEnabled> boolean checkEntitiesEnabled(Collection<? extends E> items, String name,
                                                        Function<? super E, String> stringFunction) {
        return checkEntitiesUsable(items, name+" not enabled: ", stringFunction, HasEnabled::isEnabled);
    }

    private <E> boolean checkEntitiesUsable(Collection<E> items, String message, Function<? super E, String> stringFunction,
                                       Predicate<? super E> enabledPredicate) {
        List<String> notUsable = items.stream()
                .filter(x -> x!=null && !enabledPredicate.test(x))
                .map(stringFunction)
                .sorted()
                .collect(toList());
        if (notUsable.isEmpty()) {
            return true;
        }
        addProblem(message + notUsable);
        return false;
    }
}
