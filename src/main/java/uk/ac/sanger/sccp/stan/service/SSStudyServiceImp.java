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


    /**
     * Updates the studies, triggered on a schedule.
     */
    @Scheduled(cron = "${spring.mlwh.schedule:-}", zone = "GMT")
    public void scheduledUpdate() {
        updateStudies();
    }

    @Override
    public void updateStudies() {
        log.info("Updating studies from mlwh");
        Map<Integer, SSStudy> ssStudies = ssStudyRepo.loadAllSs().stream()
                .collect(inMap(SSStudy::id));
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

    /**
     * Updates the given dnap studies with information from the given SS studies.
     * This should be called inside a transaction.
     * @param stanStudies dnap studies in stan
     * @param ssStudies Sequencescape studies loaded from the mlwh
     */
    void update(Map<Integer, DnapStudy> stanStudies, Map<Integer, SSStudy> ssStudies) {
        List<DnapStudy> toDisable = stanStudies.values().stream()
                .filter(ds -> ds.isEnabled() && ssStudies.get(ds.getSsId())==null)
                .collect(toList());

        // Any studies in stan that are disabled but appear in the mlwh can be re-enabled
        List<DnapStudy> toEnable = stanStudies.values().stream()
                .filter(ds -> !ds.isEnabled() && ssStudies.get(ds.getSsId())!=null)
                .collect(toList());

        // Any studies in stan that are enabled but do not appear in the mlwh should be disabled
        List<DnapStudy> toRename = stanStudies.values().stream()
                .filter(ds -> {
                    SSStudy ss = ssStudies.get(ds.getSsId());
                    return (ss != null && !ss.name().equals(ds.getName()));
                })
                .collect(toList());

        // Any studies in sequencescape that are not in stan should be created
        List<SSStudy> toCreate = ssStudies.values().stream()
                .filter(ss -> stanStudies.get(ss.id())==null)
                .collect(toList());

        Set<DnapStudy> updated = new HashSet<>(toDisable.size() + toEnable.size() + toRename.size() + toCreate.size());
        updated.addAll(setEnabled(toDisable, false));
        updated.addAll(setEnabled(toEnable, true));
        updated.addAll(rename(toRename, ssStudies));

        // Update all the changed dnap studies
        if (!updated.isEmpty()) {
            log.info("{} studies updated", updated.size());
            dnapStudyRepo.saveAll(updated);
        }

        // Create all the new dnap studies
        if (!toCreate.isEmpty()) {
            dnapStudyRepo.saveAll(create(toCreate));
            log.info("{} studies created", toCreate.size());
        }

        else if (updated.isEmpty()) {
            log.info(("No studies updated/created."));
        }
    }

    /**
     * Sets the enabled state of the given dnap studies. Returns the changed study objects
     * @param dss studies to update
     * @param enable whether the studies should be enabled
     * @return the updated study objects (unsaved)
     */
    List<DnapStudy> setEnabled(List<DnapStudy> dss, boolean enable) {
        dss.forEach(ds -> ds.setEnabled(enable));
        return dss;
    }

    /**
     * Sets the names state of the given dnap studies according to the given SS studies
     * @param dss studies to update
     * @param ssStudies Sequencescape studies loaded from the mlwh
     * @return the updated study objects (unsaved)
     */
    List<DnapStudy> rename(List<DnapStudy> dss, Map<Integer, SSStudy> ssStudies) {
        dss.forEach(ds -> ds.setName(ssStudies.get(ds.getSsId()).name()));
        return dss;
    }

    /**
     * Creates new (unsaved) dnap studies to be saved in Stan
     * @param ssStudies the Sequencescape studies to represent
     * @return the new dnap study objects
     */
    List<DnapStudy> create(List<SSStudy> ssStudies) {
        return ssStudies.stream()
                .map(ss -> new DnapStudy(ss.id(), ss.name()))
                .sorted(Comparator.comparing(DnapStudy::getSsId))
                .collect(toList());
    }
}
