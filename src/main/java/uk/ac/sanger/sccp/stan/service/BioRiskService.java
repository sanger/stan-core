package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.BioRiskRepo;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.emptyToNull;

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

    /**
     * Loads the indicated bio risks and validates that codes are provided and valid
     * @param problems receptacle for problems
     * @param dataStream stream of objects containing bio risk codes
     * @param codeGetter function to get a code from the given objects
     * @param codeSetter function to set a code on the given objects
     * @return map of codes to bio risks
     */
    public <E> UCMap<BioRisk> loadAndValidateBioRisks(Collection<String> problems, Stream<E> dataStream,
                                                      Function<E, String> codeGetter, BiConsumer<E, String> codeSetter) {
        boolean anyMissing = false;
        Iterable<E> datas = dataStream::iterator;
        Set<String> codeSet = new LinkedHashSet<>();
        for (E data : datas) {
            String code = codeGetter.apply(data);
            if (code!=null) {
                code = emptyToNull(code.trim());
                if (codeSetter!=null) {
                    codeSetter.accept(data, code);
                }
            }
            if (code==null) {
                anyMissing = true;
            } else {
                codeSet.add(code);
            }
        }
        if (anyMissing) {
            problems.add("Biological risk number missing.");
        }
        if (codeSet.isEmpty()) {
            return new UCMap<>(0);
        }
        UCMap<BioRisk> bioRisks = loadBioRiskMap(codeSet);
        List<String> missing = codeSet.stream()
                .filter(code -> bioRisks.get(code)==null)
                .map(BasicUtils::repr)
                .toList();
        if (!missing.isEmpty()) {
            problems.add("Unknown biological risk number: "+missing);
        }
        return bioRisks;
    }
}
