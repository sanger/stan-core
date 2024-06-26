package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.Location;
import uk.ac.sanger.sccp.stan.model.store.StoredItem;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FindRequest;
import uk.ac.sanger.sccp.stan.request.FindResult;
import uk.ac.sanger.sccp.stan.request.FindResult.FindEntry;
import uk.ac.sanger.sccp.stan.request.FindResult.LabwareLocation;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Service for finding stored labware
 * @author dr6
 */
@Service
public class FindService {
    private final LabwareService labwareService;
    private final StoreService storeService;

    private final LabwareRepo labwareRepo;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final TissueTypeRepo tissueTypeRepo;
    private final WorkRepo workRepo;
    private final SlotRepo slotRepo;

    @Autowired
    public FindService(LabwareService labwareService, StoreService storeService,
                       LabwareRepo labwareRepo, DonorRepo donorRepo, TissueRepo tissueRepo, SampleRepo sampleRepo,
                       TissueTypeRepo tissueTypeRepo, WorkRepo workRepo, SlotRepo slotRepo) {
        this.labwareService = labwareService;
        this.storeService = storeService;
        this.labwareRepo = labwareRepo;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.tissueTypeRepo = tissueTypeRepo;
        this.workRepo = workRepo;
        this.slotRepo = slotRepo;
    }

    /**
     * Perform the given find request
     * @param request the specification of what to find
     * @return the result
     */
    public FindResult find(FindRequest request) {
        validateRequest(request);
        List<LabwareSample> labwareSamples;
        if (request.getLabwareBarcode()!=null) {
            labwareSamples = findByLabwareBarcode(request.getLabwareBarcode());
        } else if (request.getTissueExternalNames()!=null) {
            labwareSamples = findByTissueExternalNames(request.getTissueExternalNames());
        } else if (request.getDonorNames()!=null) {
            labwareSamples = findByDonorNames(request.getDonorNames());
        } else if (request.getTissueTypeName()!=null) {
            labwareSamples = findByTissueType(request.getTissueTypeName());
        } else {
            labwareSamples = findByWorkNumber(request.getWorkNumber());
        }

        labwareSamples = filter(labwareSamples, request);

        List<StoredItem> storedItems = getStoredItems(labwareSamples);
        return assembleResult(request, labwareSamples, storedItems);
    }

    /**
     * Checks if the given request is valid.
     * @param request the request to validate
     * @exception IllegalArgumentException if the request is invalid
     */
    public void validateRequest(FindRequest request) {
        if (request.getDonorNames()==null && request.getTissueExternalNames()==null && request.getLabwareBarcode()==null
                && request.getTissueTypeName()==null && request.getWorkNumber()==null) {
            throw new IllegalArgumentException("Donor name or external name or labware barcode or tissue type or work number must be specified.");
        }
    }

    /**
     * Finds LabwareSamples given a labware barcode.
     * @param labwareBarcode the barcode of a piece of labware
     * @return LabwareSamples for each sample in the specified labware
     */
    public List<LabwareSample> findByLabwareBarcode(String labwareBarcode) {
        final Labware lw = labwareRepo.getByBarcode(labwareBarcode);
        return lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .distinct()
                .map(sample -> {
                    Set<String> workNumbers = getWorkNumbers(lw, sample);
                    return new LabwareSample(lw, sample, workNumbers);
                })
                .collect(toList());
    }

    /**
     * Finds LabwareSamples whose tissue external name matches the given strings which may contain wildcards.
     * @param strings external names that may use wildcards.
     * @return the LabwareSamples for tissues with matching external names
     */
    public List<LabwareSample> findByTissueExternalNames(List<String> strings) {
        if (strings.isEmpty()) {
            return List.of();
        }
        Set<Integer> tissueIds = new HashSet<>();
        for (String string: strings) {
            List<Tissue> tissues;
            if (string.indexOf('*') >= 0) {
                tissues = tissueRepo.findAllByExternalNameLike(wildcardToLikeSql(string));
            } else {
                tissues = tissueRepo.getAllByExternalName(string);
            }
            tissues.forEach(tissue -> tissueIds.add(tissue.getId()));
        }
        return findByTissueIds(tissueIds);
    }

    /**
     * Finds LabwareSamples given donor names
     * @param strings the names of a donors
     * @return LabwareSamples for each labware containing samples for the specified donors
     */
    public List<LabwareSample> findByDonorNames(List<String> strings) {
        if (strings.isEmpty()) {
            return List.of();
        }
        List<Donor> donors = donorRepo.getAllByDonorNameIn(strings);
        Set<Integer> donorIds = donors.stream().map(Donor::getId).collect(toSet());
        List<Tissue> tissues = tissueRepo.findAllByDonorIdIn(donorIds);
        return findByTissueIds(tissues.stream().map(Tissue::getId).collect(toList()));
    }

    /**
     * Finds LabwareSamples given the name of a tissue type.
     * If the name is unrecognised as a tissue type, an empty list is returned
     * @param tissueTypeName the name of a tissue type
     * @return LabwareSamples for each labware containing samples of the specified tissue type
     */
    public List<LabwareSample> findByTissueType(String tissueTypeName) {
        TissueType tissueType = tissueTypeRepo.findByName(tissueTypeName).orElse(null);
        if (tissueType==null) {
            return List.of();
        }
        List<Tissue> tissues = tissueRepo.findByTissueTypeId(tissueType.getId());
        return findByTissueIds(tissues.stream().map(Tissue::getId).collect(toList()));
    }

    /**
     * Finds LabwareSamples given ids of some tissues.
     * @param tissueIds the ids of some tissues
     * @return LabwareSamples for each labware containing samples for the specified tissues
     */
    public List<LabwareSample> findByTissueIds(Collection<Integer> tissueIds) {
        List<Sample> samples = sampleRepo.findAllByTissueIdIn(tissueIds);
        if (samples.isEmpty()) {
            return List.of();
        }
        List<Labware> labware = labwareService.findBySample(samples).stream()
                .filter(Labware::isStorable)
                .toList();
        if (labware.isEmpty()) {
            return List.of();
        }
        final Set<Integer> sampleIds = samples.stream().map(Sample::getId).collect(toSet());
        return labware.stream()
                .flatMap(lw -> labwareSamples(lw, sampleIds))
                .collect(toList());
    }

    /**
     * Finds LabwareSamples given work number.
     * @param workNumber the number of the work
     * @return LabwareSamples for each labware containing samples for the specified workNumber
     */
    public List<LabwareSample> findByWorkNumber(String workNumber) {
        Work work = workRepo.getByWorkNumber(workNumber);
        List<Integer> slotIds = work.getSampleSlotIds().stream().map(Work.SampleSlotId::getSlotId).collect(toList());
        List<Slot> slots = slotRepo.findAllByIdIn(slotIds);
        Set<Integer> labwareIds = slots.stream().map(Slot::getLabwareId).collect(toSet());
        List<Labware> labware = labwareRepo.findAllByIdIn(labwareIds);

        return labware.stream().flatMap(lw -> lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .distinct()
                .map(sample -> {
                    Set<String> workNumbers = getWorkNumbers(lw, sample);
                    return new LabwareSample(lw, sample, workNumbers);
                }))
                .collect(toList());
    }

    /**
     * Gets LabwareSamples for the given labware filtering with the given set of sample ids
     * @param lw a piece of labware
     * @param sampleIds some sample ids
     * @return LabwareSamples for each sample in the given labware whose id is in the given set of sample ids
     */
    private Stream<LabwareSample> labwareSamples(Labware lw, Set<Integer> sampleIds) {
        return lw.getSlots()
                .stream()
                .flatMap(slot -> slot.getSamples().stream())
                .filter(slot -> sampleIds.contains(slot.getId()))
                .distinct()
                .map(sample -> {
                    Set<String> workNumbers = getWorkNumbers(lw, sample);
                    return new LabwareSample(lw, sample, workNumbers);
                });
    }

    /**
     * Filters a list of labware samples according to the given find request.
     * For example, if the labware samples were looked up using a donor name, and the request also
     * specifies a tissue type, then the list is filtered to include only those of the appropriate tissue type.
     * @param lss the LabwareSamples to filter
     * @param request the request used to create the filter
     * @return the filtered list of LabwareSamples
     */
    public List<LabwareSample> filter(List<LabwareSample> lss, FindRequest request) {
        Predicate<LabwareSample> predicate = createFilter(request);
        return (predicate==null ? lss : lss.stream().filter(predicate).collect(toList()));
    }

    /**
     * Creates a predicate for filtering LabwareSamples based on the given request
     * @param request the request specifying how to filter the data
     * @return a predicate that will return true for elements that should be kept
     */
    private static Predicate<LabwareSample> createFilter(FindRequest request) {
        Predicate<LabwareSample> predicate = null;
        final String labwareBarcode = request.getLabwareBarcode();
        if (labwareBarcode!=null) {
            predicate = ls -> labwareBarcode.equalsIgnoreCase(ls.labware.getBarcode());
        }
        final List<String> donorNames = request.getDonorNames();
        if (donorNames!=null) {
            final Set<String> ucDonorNames = donorNames.stream().map(String::toUpperCase).collect(toSet());
            predicate = andPredicate(predicate,
                    ls -> ucDonorNames.contains(ls.sample.getTissue().getDonor().getDonorName().toUpperCase())
            );
        }
        final List<String> externalNames = request.getTissueExternalNames();
        if (externalNames!=null) {
            if (externalNames.size()==1 && externalNames.getFirst().indexOf('*') < 0) {
                String externalName = externalNames.getFirst();
                predicate = andPredicate(predicate,
                        ls -> externalName.equalsIgnoreCase(ls.getSample().getTissue().getExternalName())
                );
            } else {
                final Pattern pattern = makeWildcardPattern(externalNames);
                predicate = andPredicate(predicate,
                        ls -> pattern.matcher(ls.getSample().getTissue().getExternalName()).matches()
                );
            }
        }
        final String tissueTypeName = request.getTissueTypeName();
        if (tissueTypeName!=null) {
            predicate = andPredicate(predicate,
                    ls -> tissueTypeName.equalsIgnoreCase(ls.getSample().getTissue().getTissueType().getName())
            );
        }
        final String workNumber = request.getWorkNumber();
        if (workNumber!=null) {
            predicate = andPredicate(predicate,
                    ls -> ls.getWorkNumbers().contains(workNumber)
            );
        }
        final String labwareTypeName = request.getLabwareTypeName();
        if (!nullOrEmpty(labwareTypeName)) {
            predicate = andPredicate(predicate,
                    ls -> ls.getLabware().getLabwareType().getName().equalsIgnoreCase(labwareTypeName));
        }
        final String speciesName = request.getSpecies();
        if (!nullOrEmpty(speciesName)) {
            predicate = andPredicate(predicate,
                    ls -> ls.getSample().getTissue().getDonor().getSpecies().getName().equalsIgnoreCase(speciesName));
        }
        predicate = andPredicate(predicate, datePredicate(request.getCreatedMin(), request.getCreatedMax()));
        return predicate;
    }

    /**
     * Creates a predicate for LabwareSamples matching a specified date range.
     * If either date argument is null, the date range is open-ended.
     * @param min the minimum date range (inclusive) or null
     * @param max the maximum date range (inclusive) or null
     * @return a predicate, or null if both date arguments are null
     */
    private static Predicate<LabwareSample> datePredicate(LocalDate min, LocalDate max) {
        if (min==null && max==null) {
            return null;
        }
        if (min==null) {
            return ls -> (!ls.getLabware().getCreated().toLocalDate().isAfter(max));
        }
        if (max==null) {
            return ls -> (!ls.getLabware().getCreated().toLocalDate().isBefore(min));
        }
        return ls -> {
            LocalDate d = ls.getLabware().getCreated().toLocalDate();
            return (!d.isBefore(min) && !d.isAfter(max));
        };
    }

    /**
     * Combines two predicates.
     * If either is null, the other is returned.
     * Otherwise they are combined with {@link Predicate#and}.
     * @param pred1 a predicate, or null
     * @param pred2 a predicate, or null
     * @param <E> the parameter type of the predicate
     * @return a combined predicate
     */
    private static <E> Predicate<E> andPredicate(Predicate<E> pred1, Predicate<E> pred2) {
        return (pred1==null ? pred2 : pred2==null ? pred1 : pred1.and(pred2));
    }

    /**
     * Looks up storage info for the given labware samples
     * @param labwareSamples the labware samples we need storage info for
     * @return the stored items returned by the StoreService
     */
    public List<StoredItem> getStoredItems(List<LabwareSample> labwareSamples) {
        Set<String> labwareBarcodes = labwareSamples.stream()
                .map(ls -> ls.labware.getBarcode())
                .collect(toSet());
        return storeService.getStored(labwareBarcodes);
    }

    /**
     * Fetches works numbers for the given labware and sample
     * @param labware the labware to search for
     * @param sample the sample to search for
     * @return a set of work numbers
     */
    public Set<String> getWorkNumbers(Labware labware, Sample sample) {
        if (labware == null || sample == null) {
            return Set.of();
        }
        return labware.getSlots().stream().flatMap(slot -> {
            if (slot.getSamples() != null && slot.getSamples().contains(sample)) {
                Set<Work> works = workRepo.findWorkForSampleIdAndSlotId(sample.getId(), slot.getId());
                if (works != null && !works.isEmpty()) {
                    return works.stream().map(Work::getWorkNumber);
                }
            }
            return Stream.empty();
        }).collect(toSet());
    }

    /**
     * Puts together accumulated info into a FindResult.
     * Of the LabwareSamples given, only the ones with storage locations will be included in the result,
     * except if the labware is specified by its barcode in the request.
     * If the request limits the number of records that should be returned, the result will include at most
     * the number of entries specified
     * @param request the original request
     * @param labwareSamples the LabwareSamples that should be described in the result
     * @param storedItems the storage info found for the labware involved
     * @return the combined result
     */
    public FindResult assembleResult(FindRequest request, List<LabwareSample> labwareSamples, List<StoredItem> storedItems) {
        Map<String, StoredItem> storedItemMap = storedItems.stream()
                .collect(toMap(si -> si.getBarcode().toUpperCase(), si -> si));
        List<FindEntry> entries = new ArrayList<>(labwareSamples.size());
        Map<Integer, Labware> labwareMap = new HashMap<>();
        Map<Integer, Sample> sampleMap = new HashMap<>();
        Map<Integer, Location> locationMap = new HashMap<>();
        Map<Integer, LabwareLocation> labwareLocationIds = new HashMap<>();
        final String lwBarcode = request.getLabwareBarcode();
        int recordCount = 0;
        int maxRecords = request.getMaxRecords();
        if (maxRecords < 0) {
            maxRecords = Integer.MAX_VALUE;
        }
        for (LabwareSample ls : labwareSamples) {
            StoredItem si = storedItemMap.get(ls.labware.getBarcode().toUpperCase());
            if (si==null && (lwBarcode==null || !lwBarcode.equalsIgnoreCase(ls.labware.getBarcode()))) {
                // skip entries that are not stored, unless they are the exact requested labware
                continue;
            }
            ++recordCount;
            if (recordCount > maxRecords) {
                continue;
            }
            labwareMap.putIfAbsent(ls.labware.getId(), ls.labware);
            sampleMap.putIfAbsent(ls.sample.getId(), ls.sample);

            if (si!=null) {
                Location loc = si.getLocation();
                if (labwareLocationIds.get(ls.labware.getId())==null) {
                    LabwareLocation lwloc = new LabwareLocation(ls.labware.getId(), loc.getId(), si.getAddress());
                    labwareLocationIds.put(ls.labware.getId(), lwloc);
                    locationMap.putIfAbsent(loc.getId(), loc);
                }
            }
            entries.add(new FindEntry(ls.sample.getId(), ls.labware.getId(), ls.workNumbers));
        }

        return new FindResult(recordCount, entries, new ArrayList<>(sampleMap.values()),
                new ArrayList<>(labwareMap.values()), new ArrayList<>(labwareLocationIds.values()),
                new ArrayList<>(locationMap.values()));
    }

    /**
     * A labware, sample and set of work numbers used as an intermediate in finding results.
     */
    public static class LabwareSample {
        Labware labware;
        Sample sample;
        Set<String> workNumbers;

        LabwareSample(Labware labware, Sample sample, Set<String> workNumbers) {
            this.labware = labware;
            this.sample = sample;
            this.workNumbers = workNumbers;
        }

        public Labware getLabware() {
            return this.labware;
        }

        public Sample getSample() {
            return this.sample;
        }

        public Set<String> getWorkNumbers() {
            return this.workNumbers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LabwareSample that = (LabwareSample) o;
            return Objects.equals(labware, that.labware) && Objects.equals(sample, that.sample) && Objects.equals(workNumbers, that.workNumbers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(labware, sample, workNumbers);
        }

        @Override
        public String toString() {
            return String.format("(labware=%s, sample=%s, workNumbers=%s)", getLabware().getId(), getSample().getId(), getWorkNumbers());
        }
    }
}
