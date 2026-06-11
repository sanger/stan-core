package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.AddExternalIdsRequest.AddressExternalName;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

@Service
public class SampleProcessingServiceImp implements SampleProcessingService {
    private final TissueRepo tissueRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo labwareRepo;
    private final OperationService opService;
    private final Validator<String> externalNameValidator;

    @Autowired
    public SampleProcessingServiceImp(TissueRepo tissueRepo,
                                      OperationTypeRepo opTypeRepo,
                                      LabwareRepo labwareRepo,
                                      OperationService opService,
                                      @Qualifier("externalNameValidator") Validator<String> externalNameValidator) {
        this.tissueRepo = tissueRepo;
        this.opTypeRepo = opTypeRepo;
        this.labwareRepo = labwareRepo;
        this.opService = opService;
        this.externalNameValidator = externalNameValidator;
    }

    @Override
    public OperationResult addExternalID(User user, AddExternalIDRequest request) {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        Collection<String> problems = new LinkedHashSet<>();

        Labware lw = labwareRepo.getByBarcode(request.getLabwareBarcode());
        String externalName = request.getExternalName();
        Set<Sample> samples = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .collect(toSet());

        validateSamples(problems, samples);
        validateExternalName(problems, externalName);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        OperationType opType = opTypeRepo.getByName("Add external ID");
        Tissue tissue = samples.iterator().next().getTissue();
        tissue.setExternalName(externalName);
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        return new OperationResult(List.of(op), List.of(lw));
    }

    @Override
    public OperationResult addExternalIds(User user, AddExternalIdsRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        Collection<String> problems = new LinkedHashSet<>();
        Labware lw = loadLabware(problems, request.getLabwareBarcode());
        Map<Tissue, String> tissueNames = validate(problems, lw, request);
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        OperationType opType = opTypeRepo.getByName("Add external ID");
        tissueNames.forEach(Tissue::setExternalName);
        tissueRepo.saveAll(tissueNames.keySet());
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        return new OperationResult(List.of(op), List.of(lw));
    }

    /** Loads the indicated labware or records a problem. */
    Labware loadLabware(Collection<String> problems, String barcode) {
        if (nullOrEmpty(barcode)) {
            problems.add("No barcode supplied.");
            return null;
        }
        var optLw = labwareRepo.findByBarcode(barcode);
        if (optLw.isPresent()) {
            return optLw.get();
        }
        problems.add("No labware found with barcode "+repr(barcode)+".");
        return null;
    }

    /**
     * Checks the request against the indicated labware.
     * Returns a map from tissue to the indicated name for that tissue.
     * @param problems receptacle for problems found
     * @param lw the labware for the request
     * @param request the details of the request
     * @return a map from tissue to the indicated name for that tissue
     */
    Map<Tissue, String> validate(Collection<String> problems, Labware lw, AddExternalIdsRequest request) {
        if (lw==null) {
            // give up
            return Map.of();
        }
        if (lw.isEmpty()) {
            problems.add("Labware "+lw.getBarcode()+" is empty.");
            return Map.of();
        }
        if (nullOrEmpty(request.getAddressNames())) {
            problems.add("No names specified.");
            return Map.of();
        }
        Set<Address> addresses = new HashSet<>();
        for (AddressExternalName an : request.getAddressNames()) {
            Address ad = an.getAddress();
            if (!addresses.add(ad)) {
                problems.add("Repeated slot address: "+ad);
            }
        }
        if (!problems.isEmpty()) {
            return Map.of();
        }
        Map<Address, Tissue> addressTissue = makeAddressTissueMap(problems, lw, addresses);
        Map<Tissue, String> tissueNames = makeTissueNameMap(problems, addressTissue, request.getAddressNames());
        checkNames(problems, tissueNames.values());
        return tissueNames;
    }

    /**
     * Checks the addresses seem valid, returns a map from address to the tissue for that address.
     * Possible problems:
     * <ul>
     *     <li>Address not valid for labware</li>
     *     <li>Indicated slot is empty</li>
     *     <li>Indicated slot has more than one tissue</li>
     * </ul>
     * @param problems receptacle for problems
     * @param lw the labware for the request
     * @param addresses the addresses to check
     * @return map from address to the tissue indicated by that address
     */
    Map<Address, Tissue> makeAddressTissueMap(Collection<String> problems, Labware lw, Set<Address> addresses) {
        Map<Address, Tissue> map = new HashMap<>(addresses.size());
        Set<Address> invalidAddresses = new LinkedHashSet<>();
        Set<Address> emptyAddresses = new LinkedHashSet<>();
        Set<Address> overFullAddresses = new LinkedHashSet<>();
        for (Address ad : addresses) {
            Slot slot = lw.optSlot(ad).orElse(null);
            if (slot==null) {
                invalidAddresses.add(ad);
                continue;
            }
            if (slot.getSamples().isEmpty()) {
                emptyAddresses.add(ad);
                continue;
            }
            Set<Tissue> tissues = slot.getSamples().stream()
                    .map(Sample::getTissue)
                    .collect(toSet());
            if (tissues.size() > 1) {
                overFullAddresses.add(ad);
                continue;
            }
            Tissue tissue = tissues.iterator().next();
            map.put(ad, tissue);
        }
        if (!invalidAddresses.isEmpty()) {
            problems.add("Invalid slot address for labware "+lw.getBarcode()+": "+invalidAddresses);
        }
        if (!emptyAddresses.isEmpty()) {
            problems.add("Slot is empty in labware "+lw.getBarcode()+": "+emptyAddresses);
        }
        if (!overFullAddresses.isEmpty()) {
            problems.add("Slot contains more than one tissue in labware "+lw.getBarcode()+": "+overFullAddresses);
        }
        return map;
    }

    /**
     * Checks the relationship between tissues and new names.
     * Possible problems:
     * <ul>
     *     <li>Tissue already has an external name</li>
     *     <li>Tissue does not have a replicate number</li>
     *     <li>More than one name is given for the same tissue (which may be present in several slots)</li>
     * </ul>
     * @param problems receptacle for problems
     * @param addressTissue map from slot address to tissue
     * @param addressNames requested addresses and new names
     * @return a map from tissue to the new name for that tissue
     */
    Map<Tissue, String> makeTissueNameMap(Collection<String> problems, Map<Address, Tissue> addressTissue, List<AddressExternalName> addressNames) {
        Map<Tissue, String> tissueNames = new HashMap<>();
        Set<Tissue> clashes = new LinkedHashSet<>();
        Set<Address> alreadyNamed = new LinkedHashSet<>();
        Set<Address> noRep = new LinkedHashSet<>();
        for (AddressExternalName an : addressNames) {
            final Address ad = an.getAddress();
            Tissue tis = addressTissue.get(ad);
            if (tis==null) {
                continue;
            }
            if (!nullOrEmpty(tis.getExternalName())) {
                alreadyNamed.add(ad);
                continue;
            }
            if (nullOrEmpty(tis.getReplicate())) {
                noRep.add(ad);
                continue;
            }
            String mapName = tissueNames.get(tis);
            if (mapName==null) {
                tissueNames.put(tis, an.getExternalName());
            } else if (!mapName.equalsIgnoreCase(an.getExternalName())) {
                clashes.add(tis);
            }
        }
        if (!alreadyNamed.isEmpty()) {
            problems.add("Tissue already has an external name, in slot: "+alreadyNamed);
        }
        if (!noRep.isEmpty()) {
            problems.add("Tissue does not have a replicate number, in slot: "+noRep);
        }
        if (!clashes.isEmpty()) {
            List<String> tissueDescs = new ArrayList<>(clashes.size());
            for (Tissue tis : clashes) {
                String adString = addressTissue.entrySet().stream()
                        .filter(e -> tis.equals(e.getValue()))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .map(Address::toString)
                        .collect(joining("; "));
                tissueDescs.add("[tissue in slot "+adString+"]");
            }
            problems.add("Different external names given for tissue: "+tissueDescs);
        }
        return tissueNames;
    }

    /**
     * Checks for problems with the names.
     * Possible problems:
     * <ul>
     *     <li>Same name (case insensitive) given for several tissues</li>
     *     <li>External name not valid (via {@code externalNameValidator})</li>
     *     <li>Name already in use</li>
     * </ul>
     * @param problems receptacle for problems
     * @param names the new names requested
     */
    void checkNames(Collection<String> problems, Collection<String> names) {
        Set<String> distinctNames = new HashSet<>(names.size());
        Set<String> dupeNames = new LinkedHashSet<>();
        for (String name : names) {
            String nameUc = name.toUpperCase();
            if (!distinctNames.add(nameUc)) {
                dupeNames.add(repr(name));
            }
        }
        if (!dupeNames.isEmpty()) {
            problems.add("Same external name given for different tissues: "+dupeNames);
        }
        for (String name : distinctNames) {
            externalNameValidator.validate(name, problems::add);
        }
        List<Tissue> tissues = tissueRepo.findAllByExternalNameIn(distinctNames);
        if (!tissues.isEmpty()) {
            String usedNames = tissues.stream()
                    .map(Tissue::getExternalName)
                    .collect(Collectors.joining(", ", "[", "]"));
            problems.add("External name already in use: "+usedNames);
        }
    }

    /**
     * Checks for problems with the samples in the labware.
     * There should be one sample in the labware, and the tissue for that sample
     * should have a replicate number and no external name.
     * @param problems receptacle for problems
     * @param samples the samples in the labware
     */
    public void validateSamples(Collection<String> problems, Set<Sample> samples) {
        if (samples.isEmpty()) {
            problems.add("Could not find a sample associated with this labware");
            return;
        }
        if (samples.size() > 1) {
            problems.add("There are too many samples associated with this labware");
            return;
        }
        Tissue tissue = samples.iterator().next().getTissue();
        if (tissue.getExternalName() != null && !tissue.getExternalName().isEmpty()) {
            problems.add("The associated tissue already has an external identifier: " + tissue.getExternalName());
        }
        if (tissue.getReplicate() == null || tissue.getReplicate().isEmpty()) {
            problems.add("The associated tissue does not have a replicate number");
        }
    }

    /**
     * Checks for problems with the given external name.
     * @param problems receptacle for problems
     * @param externalName the given external name.
     */
    public void validateExternalName(Collection<String> problems, String externalName) {
        if (externalName==null || externalName.isEmpty()) {
            problems.add("No external identifier provided");
            return;
        }
        if (!tissueRepo.findAllByExternalName(externalName).isEmpty()) {
            problems.add("External identifier is already associated with another sample: " + externalName);
        }
        externalNameValidator.validate(externalName, problems::add);
    }

}
