package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AnalyserScanData;

import javax.persistence.EntityNotFoundException;
import java.util.List;

import static uk.ac.sanger.sccp.utils.BasicUtils.stream;

/**
 * @author dr6
 */
@Service
public class AnalyserScanDataServiceImp implements AnalyserScanDataService {
    private final LabwareRepo lwRepo;
    private final WorkRepo workRepo;
    private final LabwareProbeRepo probeRepo;
    private final OperationRepo opRepo;
    private final OperationTypeRepo opTypeRepo;

    @Autowired
    public AnalyserScanDataServiceImp(LabwareRepo lwRepo, WorkRepo workRepo, LabwareProbeRepo probeRepo,
                                      OperationRepo opRepo, OperationTypeRepo opTypeRepo) {
        this.lwRepo = lwRepo;
        this.workRepo = workRepo;
        this.probeRepo = probeRepo;
        this.opRepo = opRepo;
        this.opTypeRepo = opTypeRepo;
    }

    @Override
    public AnalyserScanData load(String barcode) throws EntityNotFoundException {
        return load(lwRepo.getByBarcode(barcode));
    }

    /** Loads the data for the given labware */
    public AnalyserScanData load(Labware lw) {
        AnalyserScanData data = new AnalyserScanData();
        data.setBarcode(lw.getBarcode());
        data.setWorkNumbers(loadWorkNumbers(lw));
        data.setProbes(loadProbes(lw));
        data.setCellSegmentationRecorded(loadCellSegmentationRecorded(lw));
        return data;
    }

    /** Loads distinct work numbers used on the given labware */
    public List<String> loadWorkNumbers(Labware lw) {
        List<Integer> workIds = workRepo.findWorkIdsForLabwareId(lw.getId());
        if (workIds.isEmpty()) {
            return List.of();
        }
        Iterable<Work> works = workRepo.findAllById(workIds);
        return stream(works).map(Work::getWorkNumber).toList();
    }

    /** Loads the names of probes recorded on the given labware */
    public List<String> loadProbes(Labware lw) {
        List<LabwareProbe> probes = probeRepo.findAllByLabwareIdIn(List.of(lw.getId()));
        if (probes.isEmpty()) {
            return List.of();
        }
        return probes.stream()
                .map(LabwareProbe::getProbePanel)
                .distinct()
                .map(ProbePanel::getName)
                .toList();
    }

    /** Has cell segmentation been recorded on the given labware? */
    public boolean loadCellSegmentationRecorded(Labware lw) {
        OperationType opType = opTypeRepo.getByName("Cell segmentation");
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        return !ops.isEmpty();
    }
}
