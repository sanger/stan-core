package uk.ac.sanger.sccp.stan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.mlwh.SSStudy;
import uk.ac.sanger.sccp.stan.mlwh.SSStudyRepo;
import uk.ac.sanger.sccp.stan.model.DnapStudy;
import uk.ac.sanger.sccp.stan.repo.DnapStudyRepo;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.stream;

/**
 * @author dr6
 */
@Service
public class SSStudyServiceImp implements SSStudyService {
    Logger log = LoggerFactory.getLogger(SSStudyServiceImp.class);

    private final Transactor transactor;
    private final SSStudyRepo ssStudyRepo;
    private final DnapStudyRepo dnapStudyRepo;

    @Autowired
    public SSStudyServiceImp(Transactor transactor, SSStudyRepo ssStudyRepo, DnapStudyRepo dnapStudyRepo) {
        this.transactor = transactor;
        this.ssStudyRepo = ssStudyRepo;
        this.dnapStudyRepo = dnapStudyRepo;
    }


    @Scheduled(cron = "${spring.mlwh.schedule:-}", zone = "GMT")
    public void scheduledUpdate() {
        updateStudies();
    }

    @Override
    public void updateStudies() {
        log.info("Updating studies from mlwh");
        Map<Integer, SSStudy> ssStudies = ssStudyRepo.loadAllSs().stream()
                .collect(inMap(SSStudy::getId));
        transactor.transact("updateStudies", () ->
        {
            Map<Integer, DnapStudy> stanStudies = stream(dnapStudyRepo.findAll()).collect(inMap(DnapStudy::getSsId));
            update(stanStudies, ssStudies);
            return null;
        });
    }

    @Override
    public List<DnapStudy> loadEnabledStudies() {
        return dnapStudyRepo.findAllByEnabled(true);
    }

    void update(Map<Integer, DnapStudy> stanStudies, Map<Integer, SSStudy> ssStudies) {
        List<DnapStudy> toDisable = stanStudies.values().stream()
                .filter(ds -> ds.isEnabled() && ssStudies.get(ds.getSsId())==null)
                .collect(toList());

        List<DnapStudy> toEnable = stanStudies.values().stream()
                .filter(ds -> !ds.isEnabled() && ssStudies.get(ds.getSsId())!=null)
                .collect(toList());

        List<DnapStudy> toRename = stanStudies.values().stream()
                .filter(ds -> {
                    SSStudy ss = ssStudies.get(ds.getSsId());
                    return (ss != null && !ss.getName().equals(ds.getName()));
                })
                .collect(toList());

        List<SSStudy> toCreate = ssStudies.values().stream()
                .filter(ss -> stanStudies.get(ss.getId())==null)
                .collect(toList());

        Set<DnapStudy> updated = new HashSet<>(toDisable.size() + toEnable.size() + toRename.size() + toCreate.size());
        updated.addAll(setEnabled(toDisable, false));
        updated.addAll(setEnabled(toEnable, true));
        updated.addAll(rename(toRename, ssStudies));

        if (!updated.isEmpty()) {
            log.info("{} studies updated", updated.size());
            dnapStudyRepo.saveAll(updated);
        }

        if (!toCreate.isEmpty()) {
            dnapStudyRepo.saveAll(create(toCreate));
            log.info("{} studies created", toCreate.size());
        }

        else if (updated.isEmpty()) {
            log.info(("No studies updated/created."));
        }
    }

    List<DnapStudy> setEnabled(List<DnapStudy> dss, boolean enable) {
        dss.forEach(ds -> ds.setEnabled(enable));
        return dss;
    }

    List<DnapStudy> rename(List<DnapStudy> dss, Map<Integer, SSStudy> ssStudies) {
        dss.forEach(ds -> ds.setName(ssStudies.get(ds.getSsId()).getName()));
        return dss;
    }

    List<DnapStudy> create(List<SSStudy> ssStudies) {
        return ssStudies.stream()
                .map(ss -> new DnapStudy(ss.getId(), ss.getName()))
                .sorted(Comparator.comparing(DnapStudy::getSsId))
                .collect(toList());
    }
}
