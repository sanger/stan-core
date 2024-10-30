package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.BioRiskRepo;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Service for dealing with {@link BioRisk}
 * @author dr6
 */
@Service
public class BioRiskService extends BaseAdminService<BioRisk, BioRiskRepo> {
    @Autowired
    public BioRiskService(BioRiskRepo repo,
                          @Qualifier("bioRiskCodeValidator") Validator<String> bioRiskCodeValidator,
                          Transactor transactor, AdminNotifyService notifyService) {
        super(repo, "BioRisk", "Code", bioRiskCodeValidator, transactor, notifyService);
    }

    @Override
    protected BioRisk newEntity(String code) {
        return new BioRisk(code);
    }

    @Override
    protected Optional<BioRisk> findEntity(BioRiskRepo repo, String string) {
        return repo.findByCode(string);
    }

    /**
     * Loads bio risks with the given codes.
     * Unrecognised codes are omitted
     * @param codes codes to load
     * @return map of code to bio risk
     */
    public UCMap<BioRisk> loadBioRiskMap(Collection<String> codes) {
        if (codes.isEmpty()) {
            return new UCMap<>(0);
        }
        List<BioRisk> bioRisks = repo.findAllByCodeIn(codes);
        return UCMap.from(bioRisks, BioRisk::getCode);
    }

    /**
     * Records the bio risks against samples
     * @param sampleIdBioRisks map of sample id to bio risks
     * @param opId the operation id to link
     */
    public void recordSampleBioRisks(Map<Integer, BioRisk> sampleIdBioRisks, final Integer opId) {
        sampleIdBioRisks.forEach((sampleId, bioRisk) -> repo.recordBioRisk(sampleId, bioRisk.getId(), opId));
    }

    /**
     * Copies bio risks from source samples to destination samples
     * @param sampleDerivations map of destination to source sampleIds
     */
    public void copySampleBioRisks(Map<Integer, Integer> sampleDerivations) {
        if (!sampleDerivations.isEmpty()) {
            Map<Integer, Integer> sourceBrIds = repo.loadBioRiskIdsForSampleIds(sampleDerivations.values());
            if (!sourceBrIds.isEmpty()) {
                Map<Integer, Integer> destBrIds = sampleDerivations.entrySet().stream()
                        .filter(e -> sourceBrIds.get(e.getValue())!=null)
                        .collect(toMap(Map.Entry::getKey, e -> sourceBrIds.get(e.getValue())));
                if (!destBrIds.isEmpty()) {
                    destBrIds.forEach((sampleId, brId) -> repo.recordBioRisk(sampleId, brId, null));
                }
            }
        }
    }

    /**
     * Copies bio risks from source samples to destination samples
     * @param actions a stream of actions, linking source to destination samples
     */
    public void copyActionSampleBioRisks(Stream<Action> actions) {
        Map<Integer, Integer> sampleDerivations = actions.filter(a -> !a.getSample().getId().equals(a.getSourceSample().getId()))
                .collect(toMap(a -> a.getSample().getId(), a -> a.getSourceSample().getId(), (v1, v2) -> v1));
        copySampleBioRisks(sampleDerivations);
    }

    /**
     * Copies bio risks from source samples to destination samples
     * @param op operation linking source to destination samples
     */
    public void copyOpSampleBioRisks(Operation op) {
        copyActionSampleBioRisks(op.getActions().stream());
    }

    /**
     * Copies bio risks from source samples to destination samples
     * @param ops operations linking source to destination samples
     */
    public void copyOpSampleBioRisks(Collection<Operation> ops) {
        copyActionSampleBioRisks(ops.stream().flatMap(op -> op.getActions().stream()));
    }
}
