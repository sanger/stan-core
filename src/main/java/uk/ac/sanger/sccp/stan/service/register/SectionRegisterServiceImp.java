package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.emptyToNull;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.UCMap.toUCMap;

/**
 * Service for dealing with section registration.
 * @author dr6
 */
@Service
public class SectionRegisterServiceImp implements IRegisterService<SectionRegisterRequest> {
    private final RegisterValidationFactory validationFactory;
    private final DonorRepo donorRepo;
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final MeasurementRepo measurementRepo;
    private final OperationTypeRepo opTypeRepo;
    private final SlotRepo slotRepo;
    private final SamplePositionRepo samplePositionRepo;

    private final OperationService opService;
    private final LabwareService lwService;
    private final WorkService workService;
    private final BioRiskService bioRiskService;

    public SectionRegisterServiceImp(RegisterValidationFactory validationFactory, DonorRepo donorRepo,
                                     TissueRepo tissueRepo, SampleRepo sampleRepo, MeasurementRepo measurementRepo,
                                     OperationTypeRepo opTypeRepo, SlotRepo slotRepo,
                                     SamplePositionRepo samplePositionRepo, OperationService opService,
                                     LabwareService lwService, WorkService workService, BioRiskService bioRiskService) {
        this.validationFactory = validationFactory;
        this.donorRepo = donorRepo;
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.measurementRepo = measurementRepo;
        this.opTypeRepo = opTypeRepo;
        this.slotRepo = slotRepo;
        this.samplePositionRepo = samplePositionRepo;
        this.opService = opService;
        this.lwService = lwService;
        this.workService = workService;
        this.bioRiskService = bioRiskService;
    }

    @Override
    public RegisterResult register(User user, SectionRegisterRequest request) {
        SectionRegisterValidation validation = validationFactory.createSectionRegisterValidation(request);
        var validationResult = validation.validate();
        validation.throwError();
        assert validationResult != null;
        return execute(user, request, validationResult);
    }

    /**
     * Performs all the tasks required for section registration using the information produced during validation.
     * @param user the user responsible for the registration
     * @param request the original request from the user
     * @param sections the information created during validation
     * @return the result of the registration
     */
    public RegisterResult execute(User user, SectionRegisterRequest request, ValidatedSections sections) {
        UCMap<Donor> donorMap = createDonors(sections.donorMap().values());
        UCMap<Tissue> tissueMap = createTissues(sections.sampleMap().values(), donorMap);
        UCMap<Sample> sampleMap = createSamples(sections.sampleMap().values(), tissueMap);
        UCMap<Labware> labwareMap = createAllLabware(request, sections.labwareTypes(), sampleMap);
        recordOperations(user, request, labwareMap, sampleMap, sections.slotRegionMap(), sections.bioRiskMap(), sections.work());
        return assembleResult(request, labwareMap, tissueMap);
    }

    /**
     * Assembles the created entities into a result that lists them in the same order they were requested.
     * @param request the request
     * @param labwareMap a map of external barcode (upper case) to labware
     * @param tissueMap a map of external identifier (upper case) to tissue
     * @return a register result
     */
    public RegisterResult assembleResult(SectionRegisterRequest request, UCMap<Labware> labwareMap,
                                         UCMap<Tissue> tissueMap) {
        List<Labware> labware = new ArrayList<>(request.getLabware().size());
        for (SectionRegisterLabware srl : request.getLabware()) {
            labware.add(labwareMap.get(srl.getExternalBarcode()));
        }
        return new RegisterResult(labware);
    }

    /**
     * Creates donors, where necessary.
     * @param donors a map of donor name (upper case) to donor. The donors may be a mix of new (unpersisted)
     *               and existing (already persisted).
     * @return a map of donor name (upper case) to donor, where every donor is now persisted.
     */
    public UCMap<Donor> createDonors(Collection<Donor> donors) {
        List<Donor> unsavedDonors = new ArrayList<>();
        UCMap<Donor> donorMap = new UCMap<>(donors.size());
        for (Donor donor : donors) {
            if (donor.getId()==null) {
                unsavedDonors.add(donor);
            } else {
                donorMap.put(donor.getDonorName(), donor);
            }
        }
        if (!unsavedDonors.isEmpty()) {
            for (Donor donor : donorRepo.saveAll(unsavedDonors)) {
                donorMap.put(donor.getDonorName(), donor);
            }
        }
        return donorMap;
    }

    /**
     * Creates tissues as described in the given unpersisted samples.
     * The tissues will be linked to donors in the given donor map.
     * @param samples some unpersisted samples containing unpersisted tissues
     * @param donorMap a map of donor name (upper case) to persisted donor
     * @return a map of external identifier (upper case) to newly created tissue
     */
    public UCMap<Tissue> createTissues(Collection<Sample> samples, UCMap<Donor> donorMap) {
        List<Tissue> tissues = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            Tissue tissue = sample.getTissue();
            if (tissue.getDonor().getId()==null) {
                tissue.setDonor(donorMap.get(tissue.getDonor().getDonorName()));
            }
            tissues.add(tissue);
        }
        UCMap<Tissue> tissueMap = new UCMap<>(samples.size());
        for (Tissue tissue : tissueRepo.saveAll(tissues)) {
            tissueMap.put(tissue.getExternalName(), tissue);
        }
        return tissueMap;
    }

    /**
     * Creates samples. The samples will be linked to tissues in the given tissue map.
     * @param samples unpersisted samples
     * @param tissueMap a map of external identifier (upper case) to persisted tissue
     * @return a map of external identifier (upper case) to newly created sample
     */
    public UCMap<Sample> createSamples(Collection<Sample> samples, UCMap<Tissue> tissueMap) {
        for (Sample sample : samples) {
            sample.setTissue(tissueMap.get(sample.getTissue().getExternalName()));
        }
        UCMap<Sample> sampleMap = new UCMap<>(samples.size());
        for (Sample sample : sampleRepo.saveAll(samples)) {
            sampleMap.put(sample.getTissue().getExternalName(), sample);
        }
        return sampleMap;
    }

    /**
     * Creates labware. The labware will contain samples as described in the request and given in the sample map.
     * @param request the specification of the labware to create
     * @param labwareTypes the types of labware
     * @param sampleMap a map of external identifier (upper case) to sample
     * @return a map of external barcode (upper case) to labware
     */
    public UCMap<Labware> createAllLabware(SectionRegisterRequest request, UCMap<LabwareType> labwareTypes,
                                           UCMap<Sample> sampleMap) {
        return request.getLabware().stream()
                .map(srl -> createLabware(srl, labwareTypes, sampleMap))
                .collect(toUCMap(Labware::getExternalBarcode));
    }

    /**
     * Creates a new item of labware containing the samples as described in the request
     * @param srl the specification of the labware
     * @param labwareTypes a map of name (upper case) to labware type
     * @param sampleMap the available samples, a map from external identifier (upper case) to sample
     * @return an item of labware containing samples from the given sample map as specified in the request
     */
    public Labware createLabware(SectionRegisterLabware srl, UCMap<LabwareType> labwareTypes,
                                 UCMap<Sample> sampleMap) {
        LabwareType lt = labwareTypes.get(srl.getLabwareType());
        String externalBarcode = srl.getExternalBarcode();
        String prebarcode = emptyToNull(srl.getPreBarcode());
        if (lt.isPrebarcoded() && prebarcode==null) {
            prebarcode = externalBarcode;
        }
        Labware lw = lwService.create(lt, prebarcode, externalBarcode);
        for (var content : srl.getContents()) {
            Slot slot = lw.getSlot(content.getAddress());
            Sample sample = sampleMap.get(content.getExternalIdentifier());
            slot.getSamples().add(sample);
            slotRepo.save(slot);
        }
        return lw;
    }

    /**
     * Records all the register operations, one for each labware.
     * Also records thickness measurements as specified in the request
     * @param user the user responsible
     * @param request the request of what to register
     * @param labwareMap the new labware, mapped from external barcode (upper case)
     * @param sampleMap a map of external identifier (upper case) to sample
     * @param work the work to link the operations to
     * @return a list of operations
     */
    public List<Operation> recordOperations(User user, SectionRegisterRequest request, UCMap<Labware> labwareMap,
                                            UCMap<Sample> sampleMap, UCMap<SlotRegion> regionMap, UCMap<BioRisk> bioRiskMap,
                                            Work work) {
        OperationType opType = opTypeRepo.getByName("Register");
        List<Operation> ops = new ArrayList<>(request.getLabware().size());
        for (SectionRegisterLabware srl : request.getLabware()) {
            Labware lw = labwareMap.get(srl.getExternalBarcode());
            Operation op = createOp(user, opType, lw);
            ops.add(op);
            createMeasurements(srl, lw, op, sampleMap);
            createSamplePositions(srl, lw, op.getId(), sampleMap, regionMap);
            linkBioRisks(srl, op.getId(), sampleMap, bioRiskMap);
        }
        if (work != null) {
            workService.link(work, ops);
        }
        return ops;
    }

    /**
     * Creates an operation in place, with an action for every slot/sample in the given labware.
     * Delegates to {@link OperationService}
     * @param user the user responsible for the operation
     * @param opType the type of operation
     * @param lw the labware
     * @return a new operation
     */
    public Operation createOp(User user, OperationType opType, Labware lw) {
        List<Action> actions = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream()
                .map(sample -> new Action(null, null, slot, slot, sample, sample)))
                .collect(toList());
        return opService.createOperation(opType, user, actions, null);
    }

    /**
     * Creates measurements as described in the request
     * @param srl the request pertaining to one item of labware
     * @param lw the item of labware
     * @param op the operation linked to the measurement
     * @param sampleMap map of external identifier (upper case) to sample
     * @return the measurements created
     */
    public Iterable<Measurement> createMeasurements(SectionRegisterLabware srl, Labware lw, Operation op,
                                                    UCMap<Sample> sampleMap) {
        List<Measurement> measurements = new ArrayList<>();
        for (var src : srl.getContents()) {
            if (src.getDateSectioned() == null && src.getSectionThickness() == null) {
                continue;
            }
            Sample sample = sampleMap.get(src.getExternalIdentifier());
            Slot slot = lw.getSlot(src.getAddress());
            if (src.getSectionThickness() != null) {
                measurements.add(new Measurement(null, "Thickness", src.getSectionThickness().toString(),
                        sample.getId(), op.getId(), slot.getId()));
            }
            if (src.getDateSectioned() != null) {
                measurements.add(new Measurement(null, "Date sectioned", src.getDateSectioned().toString(),
                        sample.getId(), op.getId(), slot.getId()));
            }
        }
        return (measurements.isEmpty() ? measurements : measurementRepo.saveAll(measurements));
    }

    /**
     * Creates sample positions as indicated by the request
     * @param srl the request pertaining to one item of labware
     * @param lw the item of labware
     * @param opId the operation id
     * @param sampleMap map of external identifier to sample
     * @param regionMap map of slot regions from their names
     * @return the sample positions created
     */
    public Iterable<SamplePosition> createSamplePositions(SectionRegisterLabware srl, Labware lw, Integer opId,
                                                          UCMap<Sample> sampleMap, UCMap<SlotRegion> regionMap) {
        List<SamplePosition> samps = srl.getContents().stream()
                .filter(src -> !nullOrEmpty(src.getRegion()))
                .map(src -> new SamplePosition(lw.getSlot(src.getAddress()).getId(),
                        sampleMap.get(src.getExternalIdentifier()).getId(),
                        regionMap.get(src.getRegion()), opId))
                .collect(toList());
        return (samps.isEmpty() ? samps : samplePositionRepo.saveAll(samps));
    }

    /**
     * Links the indicated bio risks to the new samples
     * @param srl relevant part of the request
     * @param opId operation id
     * @param sampleMap samples mapped from external id
     * @param bioRiskMap bio risks mapped from their codes
     */
    public void linkBioRisks(SectionRegisterLabware srl, Integer opId, UCMap<Sample> sampleMap,
                             UCMap<BioRisk> bioRiskMap) {
        Map<Integer, BioRisk> sampleBioRisks = srl.getContents().stream()
                .collect(toMap(src -> sampleMap.get(src.getExternalIdentifier()).getId(), src -> bioRiskMap.get(src.getBioRiskCode())));
        bioRiskService.recordSampleBioRisks(sampleBioRisks, opId);
    }
}
