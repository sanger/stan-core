package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.TissueRepo;
import uk.ac.sanger.sccp.stan.request.NextReplicateData;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class NextReplicateServiceImp implements NextReplicateService {
    private final LabwareRepo lwRepo;
    private final TissueRepo tissueRepo;

    @Autowired
    public NextReplicateServiceImp(LabwareRepo lwRepo, TissueRepo tissueRepo) {
        this.lwRepo = lwRepo;
        this.tissueRepo = tissueRepo;
    }

    @Override
    public List<NextReplicateData> getNextReplicateData(Collection<String> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) {
            return List.of();
        }
        Map<RepKey, Set<String>> groups = groupBarcodes(barcodes);

        return groups.entrySet().stream()
                .map(e -> toReplicateData(e.getKey(), e.getValue()))
                .collect(toList());
    }

    public Map<RepKey, Set<String>> groupBarcodes(Collection<String> barcodes) {
        List<Labware> labware = lwRepo.getByBarcodeIn(barcodes);
        Map<RepKey, Set<String>> groups = new HashMap<>();
        Set<String> seenBarcodes = new HashSet<>(barcodes.size());
        for (Labware lw : labware) {
            String barcode = lw.getBarcode().toUpperCase();
            if (!seenBarcodes.add(barcode)) {
                continue;
            }
            Tissue tissue = getSingleTissue(lw);
            RepKey repKey = new RepKey(tissue);
            groups.computeIfAbsent(repKey, k -> new HashSet<>()).add(barcode);
        }
        return groups;
    }

    public NextReplicateData toReplicateData(RepKey key, Set<String> barcodes) {
        Integer repInteger = tissueRepo.findMaxReplicateForDonorIdAndSpatialLocationId(key.donorId(), key.spatialLocationId());
        int nextRep = (repInteger == null ? 1 : (repInteger + 1));
        return new NextReplicateData(new ArrayList<>(barcodes), key.donorId(), key.spatialLocationId(), nextRep);
    }

    public Tissue getSingleTissue(Labware lw) {
        Tissue tissue = null;
        for (Slot slot : lw.getSlots()) {
            for (Sample sample : slot.getSamples()) {
                if (tissue == null) {
                    tissue = sample.getTissue();
                } else if (!tissue.equals(sample.getTissue())) {
                    throw new IllegalArgumentException("Labware " + lw.getBarcode() + " contains multiple different tissues.");
                }
            }
        }
        if (tissue == null) {
            throw new IllegalArgumentException("Labware " + lw.getBarcode() + " is empty.");
        }
        return tissue;
    }

    record RepKey(int donorId, int spatialLocationId) {
        RepKey(Tissue tissue) {
            this(tissue.getDonor().getId(), tissue.getSpatialLocation().getId());
        }
    }
}

