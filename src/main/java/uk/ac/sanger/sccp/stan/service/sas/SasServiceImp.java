package uk.ac.sanger.sccp.stan.service.sas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.SasNumber.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

@Service
public class SasServiceImp implements SasService {
    private final ProjectRepo projectRepo;
    private final CostCodeRepo costCodeRepo;
    private final SasTypeRepo sasTypeRepo;
    private final SasNumberRepo sasRepo;
    private final SasEventService sasEventService;

    @Autowired
    public SasServiceImp(ProjectRepo projectRepo, CostCodeRepo costCodeRepo, SasTypeRepo sasTypeRepo, SasNumberRepo sasRepo,
                         SasEventService sasEventService) {
        this.projectRepo = projectRepo;
        this.costCodeRepo = costCodeRepo;
        this.sasTypeRepo = sasTypeRepo;
        this.sasRepo = sasRepo;
        this.sasEventService = sasEventService;
    }

    public void checkPrefix(String prefix) {
        if (prefix==null || prefix.isBlank()) {
            throw new IllegalArgumentException("No prefix supplied for SAS number.");
        }
        if (!prefix.equalsIgnoreCase("SAS") && !prefix.equalsIgnoreCase("R&D")) {
            throw new IllegalArgumentException("Invalid SAS number prefix: "+repr(prefix));
        }
    }

    @Override
    public SasNumber createSasNumber(User user, String prefix, String sasTypeName, String projectName, String costCode) {
        checkPrefix(prefix);

        Project project = projectRepo.getByName(projectName);
        CostCode cc = costCodeRepo.getByCode(costCode);
        SasType type = sasTypeRepo.getByName(sasTypeName);

        String sasNum = sasRepo.createNumber(prefix);
        SasNumber sas = sasRepo.save(new SasNumber(null, sasNum, type, project, cc, Status.active));
        sasEventService.recordEvent(user, sas, SasEvent.Type.create, null);
        return sas;
    }

    @Override
    public SasNumber updateStatus(User user, String sasNum, Status newStatus, Integer commentId) {
        SasNumber sas = sasRepo.getBySasNumber(sasNum);
        sasEventService.recordStatusChange(user, sas, newStatus, commentId);
        sas.setStatus(newStatus);
        return sasRepo.save(sas);
    }

    @Override
    public SasNumber link(String sasNumber, Collection<Operation> operations) {
        SasNumber sas = sasRepo.getBySasNumber(sasNumber);
        return link(sas, operations);
    }

    @Override
    public SasNumber link(SasNumber sas, Collection<Operation> operations) {
        if (operations.isEmpty()) {
            return sas;
        }
        if (sas.getStatus()!=Status.active) {
            throw new IllegalArgumentException(sas.getSasNumber()+" cannot be used because it is "+sas.getStatus()+".");
        }
        List<Integer> opIds = sas.getOperationIds();
        if (!(opIds instanceof ArrayList)) {
            opIds = newArrayList(opIds);
        }
        List<SampleSlotId> ssIds = sas.getSampleSlotIds();
        if (!(ssIds instanceof ArrayList)) {
            ssIds = newArrayList(ssIds);
        }
        Set<SampleSlotId> seenSsIds = new HashSet<>(ssIds);
        for (Operation op : operations) {
            opIds.add(op.getId());
            for (Action action : op.getActions()) {
                SampleSlotId ssId = new SampleSlotId(action.getSample().getId(), action.getDestination().getId());
                if (seenSsIds.add(ssId)) {
                    ssIds.add(ssId);
                }
            }
        }

        sas.setOperationIds(opIds);
        sas.setSampleSlotIds(ssIds);
        return sasRepo.save(sas);
    }

    @Override
    public SasNumber getUsableSas(String sasNumber) {
        requireNonNull(sasNumber, "SAS number is null.");
        SasNumber sas = sasRepo.getBySasNumber(sasNumber);
        if (!sas.isUsable()) {
            throw new IllegalArgumentException(sas.getSasNumber()+" cannot be used because it is "+sas.getStatus()+".");
        }
        return sas;
    }

    @Override
    public SasNumber validateUsableSas(Collection<String> problems, String sasNumber) {
        if (sasNumber==null) {
            return null;
        }
        Optional<SasNumber> optSas = sasRepo.findBySasNumber(sasNumber);
        if (optSas.isEmpty()) {
            problems.add("SAS number not recognised: "+repr(sasNumber));
            return null;
        }
        SasNumber sas = optSas.get();
        if (!sas.isUsable()) {
            problems.add(sas.getSasNumber()+" cannot be used because it is "+sas.getStatus()+".");
        }
        return sas;
    }
}
