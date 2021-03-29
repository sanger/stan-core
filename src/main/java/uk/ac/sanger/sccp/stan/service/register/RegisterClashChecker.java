package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;

import java.util.*;

import static java.util.stream.Collectors.*;


/**
 * Service to find and describe external name clashes in block registration requests
 * @author dr6
 */
@Service
public class RegisterClashChecker {
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;
    private final LabwareRepo lwRepo;

    @Autowired
    public RegisterClashChecker(TissueRepo tissueRepo, SampleRepo sampleRepo, OperationTypeRepo opTypeRepo,
                                OperationRepo opRepo, LabwareRepo lwRepo) {
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.lwRepo = lwRepo;
    }

    public List<RegisterClash> findClashes(RegisterRequest request) {
        Set<String> externalNames = request.getBlocks().stream()
                .filter(br -> !br.isExistingTissue())
                .map(BlockRegisterRequest::getExternalIdentifier)
                .collect(toSet());
        if (externalNames.isEmpty()) {
            return List.of();
        }
        List<Tissue> existingTissues = tissueRepo.findAllByExternalNameIn(externalNames);
        if (existingTissues.isEmpty()) {
            return List.of();
        }
        return createClashInfo(existingTissues);
    }

    public List<RegisterClash> createClashInfo(List<Tissue> tissues) {
        Set<Integer> tissueIds = tissues.stream().map(Tissue::getId).collect(toSet());
        Set<Integer> sampleIds = loadSampleIds(tissueIds);
        OperationType opType = opTypeRepo.getByName("Register");
        List<Operation> registrations = opRepo.findAllByOperationTypeAndSampleIdIn(opType, sampleIds);
        Map<Integer, List<Labware>> tissueIdLabwareMap = createTissueIdLabwareMap(tissueIds, registrations);
        return tissues.stream()
                .map(t -> toRegisterClash(t, tissueIdLabwareMap))
                .collect(toList());
    }

    public Set<Integer> loadSampleIds(Collection<Integer> tissueIds) {
        return sampleRepo.findAllByTissueIdIn(tissueIds).stream()
                .map(Sample::getId)
                .collect(toSet());
    }

    public Map<Integer, List<Labware>> createTissueIdLabwareMap(Set<Integer> tissueIds, Collection<Operation> ops) {
        Map<Integer, Set<Integer>> tissueIdLabwareIdMap = tissueIds.stream()
                .collect(toMap(id -> id, id -> new HashSet<>()));
        Set<Integer> labwareIdSet = new HashSet<>();
        for (Operation op : ops) {
            for (Action action : op.getActions()) {
                Integer tissueId = action.getSample().getTissue().getId();
                if (tissueIds.contains(tissueId)) {
                    Integer lwId = action.getDestination().getLabwareId();
                    tissueIdLabwareIdMap.get(tissueId).add(lwId);
                    labwareIdSet.add(lwId);
                }
            }
        }
        Map<Integer, Labware> labwareIdMap = lwRepo.findAllByIdIn(labwareIdSet).stream()
                .collect(toMap(Labware::getId, lw -> lw));

        Map<Integer, List<Labware>> tissueIdLabwareMap = new HashMap<>(tissueIdLabwareIdMap.size());

        for (var entry : tissueIdLabwareIdMap.entrySet()) {
            Integer tissueId = entry.getKey();
            List<Labware> labware = entry.getValue().stream()
                    .map(labwareIdMap::get)
                    .collect(toList());
            tissueIdLabwareMap.put(tissueId, labware);
        }
        return tissueIdLabwareMap;
    }

    public RegisterClash toRegisterClash(Tissue tissue, Map<Integer, List<Labware>> tissueIdLabwareMap) {
        List<Labware> labware = tissueIdLabwareMap.get(tissue.getId());
        if (labware==null) {
            labware = List.of();
        }
        return new RegisterClash(tissue, labware);
    }
}
