package uk.ac.sanger.sccp.stan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for creating persisted entities for tests
 * @author dr6
 */
@TestComponent
public class EntityCreator {
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private DonorRepo donorRepo;
    @Autowired
    private TissueRepo tissueRepo;
    @Autowired
    private SpatialLocationRepo slRepo;
    @Autowired
    private MouldSizeRepo mouldSizeRepo;
    @Autowired
    private MediumRepo mediumRepo;
    @Autowired
    private FixativeRepo fixativeRepo;
    @Autowired
    private HmdmcRepo hmdmcRepo;
    @Autowired
    private SampleRepo sampleRepo;
    @Autowired
    private LabwareRepo labwareRepo;
    @Autowired
    private LabwareTypeRepo ltRepo;
    @Autowired
    private SlotRepo slotRepo;

    public User createUser(String username) {
        return userRepo.save(new User(null, username));
    }

    public Donor createDonor(String donorName, LifeStage lifeStage) {
        return donorRepo.save(new Donor(null, donorName, lifeStage));
    }

    public Tissue createTissue(Donor donor, String externalName) {
        return tissueRepo.save(new Tissue(null, externalName, 1, getAny(slRepo), donor, getAny(mouldSizeRepo),
                getAny(mediumRepo), getAny(fixativeRepo), getAny(hmdmcRepo)));
    }

    public Sample createSample(Tissue tissue, Integer section) {
        return sampleRepo.save(new Sample(null, section, tissue));
    }

    public Labware createTube(String barcode) {
        LabwareType lt = ltRepo.getByName("Tube");
        Labware lw = labwareRepo.save(new Labware(null, barcode, lt, null));
        Slot slot = slotRepo.save(new Slot(null, lw.getId(), new Address(1,1), new ArrayList<>(), null, null));
        lw.getSlots().add(slot);
        return lw;
    }

    public Labware createBlock(String barcode, Sample sample) {
        LabwareType lt = ltRepo.getByName("Proviasette");
        Labware lw = labwareRepo.save(new Labware(null, barcode, lt, null));
        Slot slot = slotRepo.save(new Slot(null, lw.getId(), new Address(1,1), new ArrayList<>(List.of(sample)), sample.getId(), 0));
        lw.getSlots().add(slot);
        return lw;
    }

    public <E> E getAny(CrudRepository<E, ?> repo) {
        return repo.findAll().iterator().next();
    }
}
