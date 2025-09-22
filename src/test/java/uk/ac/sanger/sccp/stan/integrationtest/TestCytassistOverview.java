package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.cytassistoverview.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

/**
 * Tests {@link CytassistOverviewDataCompilerImp}
 * and {@link CytassistOverviewServiceImp}
 * @author dr6
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({EntityCreator.class})
public class TestCytassistOverview {
    @Autowired
    EntityCreator entityCreator;
    @Autowired
    EntityManager entityManager;
    @Autowired
    CytassistOverviewService coService;
    @Autowired
    CytassistOverviewRepo coRepo;
    @Autowired
    OperationTypeRepo opTypeRepo;
    @Autowired
    WorkRepo workRepo;
    @Autowired
    StainTypeRepo stainTypeRepo;
    @Autowired
    ProbePanelRepo probePanelRepo;
    @Autowired
    LabwareProbeRepo lwProbeRepo;
    @Autowired
    MeasurementRepo measurementRepo;
    @Autowired
    ReagentActionRepo raRepo;
    @Autowired
    ReagentPlateRepo rpRepo;
    @Autowired
    ReagentSlotRepo rsRepo;
    @Autowired
    CommentRepo commentRepo;
    @Autowired
    OperationCommentRepo opComRepo;
    @Autowired
    ReleaseRepo releaseRepo;
    @Autowired
    LabwareRepo lwRepo;
    @Autowired
    SnapshotRepo snapshotRepo;
    @Autowired
    LabwareFlagRepo flagRepo;
    @Autowired
    LabwareNoteRepo noteRepo;

    User user;
    List<Operation> opsToUpdate;

    @Test
    @Transactional
    public void testUpdate() {
        user = entityCreator.createUser("user1");
        opsToUpdate = new ArrayList<>();
        Work work1 = entityCreator.createWork(null, null, null,null, null);
        Work work2 = entityCreator.createWorkLike(work1);
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.getTubeType();
        Labware[] lws = IntStream.range(0, 7)
                .mapToObj(i -> entityCreator.createLabware("STAN-"+i, lt, sample))
                .toArray(Labware[]::new);
        Slot[] slots = Arrays.stream(lws).map(Labware::getFirstSlot).toArray(Slot[]::new);
        OperationType regOpType = opTypeRepo.getByName("Register");
        OperationType secOpType = opTypeRepo.getByName("Section");
        Operation regOp = createOp(regOpType, 0, lws[0], lws[0]);
        createOp(regOpType, 1, lws[1], lws[1]);
        Operation secOp = createOp(secOpType, 2, lws[1], lws[2]);
        work1.getOperationIds().add(regOp.getId());
        work2.getOperationIds().add(secOp.getId());
        OperationType stainOpType = opTypeRepo.getByName("Stain");
        Operation stainOp1 = createOp(stainOpType, 3, lws[0], lws[0]);
        List<StainType> stainTypes = asList(stainTypeRepo.findAll());
        stainTypeRepo.saveOperationStainTypes(stainOp1.getId(), stainTypes);
        Operation stainOp2 = createOp(stainOpType, 4, lws[2], lws[2]);
        stainTypeRepo.saveOperationStainTypes(stainOp2.getId(), stainTypes.subList(0,1));
        OperationType imageOpType = entityCreator.createOpType("Image", null, OperationTypeFlag.IN_PLACE);
        createOp(imageOpType, 5, lws[0], lws[0]);
        createOp(imageOpType, 6, lws[2], lws[2]);
        OperationType probeOpType = entityCreator.createOpType("Probe hybridisation Cytassist", null, OperationTypeFlag.IN_PLACE);
        Operation probeOp1 = createOp(probeOpType, 7, lws[0], lws[0]);
        Operation probeOp2 = createOp(probeOpType, 8, lws[2], lws[2]);
        ProbePanel pp1 = probePanelRepo.save(new ProbePanel(ProbePanel.ProbeType.cytassist, "pp1"));
        ProbePanel pp2 = probePanelRepo.save(new ProbePanel(ProbePanel.ProbeType.cytassist, "pp2"));
        lwProbeRepo.saveAll(List.of(
                new LabwareProbe(null, pp1, probeOp1.getId(), lws[0].getId(), "lot1", 11),
                new LabwareProbe(null, pp2, probeOp2.getId(), lws[2].getId(), "lot2", 12)
        ));
        OperationType probeQc = entityCreator.createOpType("Probe hybridisation QC", null, OperationTypeFlag.IN_PLACE);
        createOp(probeQc, 9, lws[0], lws[0]);
        createOp(probeQc, 10, lws[2], lws[2]);
        OperationType tcOpType = entityCreator.createOpType("Tissue coverage", null, OperationTypeFlag.IN_PLACE);
        Operation tc1 = createOp(tcOpType, 11, lws[0], lws[0]);
        Operation tc2 = createOp(tcOpType, 12, lws[2], lws[2]);
        measurementRepo.saveAll(List.of(
                new Measurement(null, "Tissue coverage", "10", sample.getId(), tc1.getId(), slots[0].getId()),
                new Measurement(null, "Tissue coverage", "20", sample.getId(), tc2.getId(), slots[2].getId())
        ));
        OperationType cytOpType = entityCreator.createOpType("Cytassist", null, OperationTypeFlag.IN_PLACE);
        Operation cyt1 = createOp(cytOpType, 16, lws[0], lws[3]);
        Operation cyt2 = createOp(cytOpType, 17, lws[2], lws[4]);
        work1.getOperationIds().add(cyt1.getId());
        work2.getOperationIds().add(cyt2.getId());
        noteRepo.saveAll(List.of(
                new LabwareNote(null, lws[3].getId(), cyt1.getId(), "lp number", "12"),
                new LabwareNote(null, lws[4].getId(), cyt2.getId(), "lp number", "13")
        ));
        OperationType transfer = entityCreator.createOpType("Transfer", null);
        createOp(transfer, 18, lws[0], lws[5]); // Just here to move to new labware
        createOp(transfer, 19, lws[2], lws[6]);
        OperationType qpcrOpType = entityCreator.createOpType("qPCR results", null, OperationTypeFlag.IN_PLACE);
        Operation qp1 = createOp(qpcrOpType, 20, lws[5], lws[5]);
        Operation qp2 = createOp(qpcrOpType, 21, lws[6], lws[6]);
        OperationType ampOpType = entityCreator.createOpType("Amplification", null, OperationTypeFlag.IN_PLACE);
        Operation amp1 = createOp(ampOpType, 22, lws[5], lws[5]);
        Operation amp2 = createOp(ampOpType, 23, lws[6], lws[6]);
        measurementRepo.saveAll(List.of(
                new Measurement(null, "Cq value", "30", sample.getId(), qp1.getId(), slots[5].getId()),
                new Measurement(null, "Cq value", "40", sample.getId(), qp2.getId(), slots[6].getId()),
                new Measurement(null, "Cq value", "50", sample.getId(), amp1.getId(), slots[5].getId()),
                new Measurement(null, "Cq value", "60", sample.getId(), amp2.getId(), slots[6].getId())
        ));
        OperationType dualOpType = entityCreator.createOpType("Dual index plate", null);
        Operation do1 = createOp(dualOpType, 24, lws[5], lws[5]);
        Operation do2 = createOp(dualOpType, 25, lws[6], lws[6]);
        ReagentPlate rp1 = createReagentPlate("RP1", ReagentPlate.REAGENT_PLATE_TYPES.getFirst());
        ReagentPlate rp2 = createReagentPlate("RP2", ReagentPlate.REAGENT_PLATE_TYPES.get(1));
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        raRepo.saveAll(List.of(
                new ReagentAction(null, do1.getId(), rp1.getSlot(A1), slots[5]),
                new ReagentAction(null, do2.getId(), rp2.getSlot(A2), slots[6])
        ));
        OperationType vcOpType = entityCreator.createOpType("Visium concentration", null);
        Operation vc1 = createOp(vcOpType, 26, lws[5], lws[5]);
        Operation vc2 = createOp(vcOpType, 27, lws[6], lws[6]);
        measurementRepo.saveAll(List.of(
                new Measurement(null, "cDNA concentration", "70", sample.getId(), vc1.getId(), slots[5].getId()),
                new Measurement(null, "Library concentration", "80", sample.getId(), vc2.getId(), slots[6].getId()),
                new Measurement(null, "Average size", "75", sample.getId(), vc2.getId(), slots[6].getId())
        ));
        Comment com = commentRepo.save(new Comment(null, "1-2", "size range"));
        opComRepo.save(new OperationComment(null, com, vc2.getId(), sample.getId(), slots[6].getId(), null));
        Snapshot snap = snapshotRepo.save(new Snapshot(lws[6].getId()));

        Release rel = releaseRepo.save(new Release(lws[6], user, entityCreator.createReleaseDestination("moon"), entityCreator.createReleaseRecipient("man"), snap.getId()));
        rel.setReleased(time(28));
        releaseRepo.save(rel);
        lws[6].setReleased(true);
        lwRepo.save(lws[5]);

        flagRepo.save(new LabwareFlag(null, lws[6], "Strange flag", user, vc1.getId(), LabwareFlag.Priority.flag));

        workRepo.saveAll(List.of(work1, work2));
        entityManager.flush();

        coService.update();
        List<CytassistOverview> cos = asList(coRepo.findAll());
        assertThat(cos).hasSize(2);
        if (cos.getFirst().getSourceBarcode().equalsIgnoreCase(lws[1].getBarcode())) {
            cos = List.of(cos.get(1), cos.get(0));
        }

        CytassistOverview co = cos.get(0);
        assertNotNull(co.getId());
        assertEquals(work1.getWorkNumber(), co.getWorkNumber());
        assertEquals(sample.getId(), co.getSampleId());
        assertNull(co.getSection());
        assertEquals(lws[0].getBarcode(), co.getSourceBarcode());
        assertEquals("A1", co.getSourceSlotAddress());
        assertEquals(lt.getName(), co.getSourceLabwareType());
        assertEquals(sample.getTissue().getExternalName(), co.getSourceExternalName());
        assertEquals(time(0), co.getSourceLabwareCreated());
        assertThat(co.getStainType().split(", ")).containsExactlyInAnyOrderElementsOf(() -> stainTypes.stream().map(StainType::getName).iterator());
        assertEquals(stainOp1.getPerformed(), co.getStainPerformed());
        assertEquals(time(5), co.getImagePerformed());
        assertEquals("pp1", co.getProbePanels());
        assertEquals(time(7), co.getProbeHybStart());
        assertEquals(time(9), co.getProbeHybEnd());
        assertEquals(lws[3].getBarcode(), co.getCytassistBarcode());
        assertEquals(lws[3].getLabwareType().getName(), co.getCytassistLabwareType());
        assertEquals("A1", co.getCytassistSlotAddress());
        assertEquals("12", co.getCytassistLp());
        assertEquals(cyt1.getPerformed(), co.getCytassistPerformed());
        assertEquals("10", co.getTissueCoverage());
        assertEquals("30", co.getQpcrResult());
        assertEquals("50", co.getAmplificationCq());
        assertEquals(ReagentPlate.REAGENT_PLATE_TYPES.getFirst(), co.getDualIndexPlateType());
        assertEquals("A1", co.getDualIndexPlateWell());
        assertEquals("cDNA concentration", co.getVisiumConcentrationType());
        assertEquals("70", co.getVisiumConcentrationValue());
        assertNull(co.getVisiumConcentrationAverageSize());
        assertNull(co.getVisiumConcentrationRange());
        assertEquals(lws[5].getBarcode(), co.getLatestBarcode());
        assertEquals(Labware.State.active.toString(), co.getLatestLwState());
        assertEquals("Tissue", co.getLatestBioState());
        assertNull(co.getLatestBarcodeReleased());
        assertNull(co.getFlags());
        assertEquals(user.getUsername(), co.getUsers());

        co = cos.get(1);
        assertNotNull(co.getId());
        assertEquals(work2.getWorkNumber(), co.getWorkNumber());
        assertEquals(sample.getId(), co.getSampleId());
        assertNull(co.getSection());
        assertEquals(lws[2].getBarcode(), co.getSourceBarcode());
        assertEquals("A1", co.getSourceSlotAddress());
        assertEquals(lt.getName(), co.getSourceLabwareType());
        assertEquals(sample.getTissue().getExternalName(), co.getSourceExternalName());
        assertEquals(time(2), co.getSourceLabwareCreated());
        assertEquals(stainTypes.getFirst().getName(), co.getStainType());
        assertEquals(stainOp2.getPerformed(), co.getStainPerformed());
        assertEquals(time(6), co.getImagePerformed());
        assertEquals("pp2", co.getProbePanels());
        assertEquals(time(8), co.getProbeHybStart());
        assertEquals(time(10), co.getProbeHybEnd());
        assertEquals(lws[4].getBarcode(), co.getCytassistBarcode());
        assertEquals(lws[4].getLabwareType().getName(), co.getCytassistLabwareType());
        assertEquals("A1", co.getCytassistSlotAddress());
        assertEquals("13", co.getCytassistLp());
        assertEquals(cyt2.getPerformed(), co.getCytassistPerformed());
        assertEquals("20", co.getTissueCoverage());
        assertEquals("40", co.getQpcrResult());
        assertEquals("60", co.getAmplificationCq());
        assertEquals(ReagentPlate.REAGENT_PLATE_TYPES.get(1), co.getDualIndexPlateType());
        assertEquals("A2", co.getDualIndexPlateWell());
        assertEquals("Library concentration", co.getVisiumConcentrationType());
        assertEquals("80", co.getVisiumConcentrationValue());
        assertEquals("75", co.getVisiumConcentrationAverageSize());
        assertEquals("1-2", co.getVisiumConcentrationRange());
        assertEquals(lws[6].getBarcode(), co.getLatestBarcode());
        assertEquals(Labware.State.released.toString(), co.getLatestLwState());
        assertEquals(time(28), co.getLatestBarcodeReleased());
        assertEquals("Strange flag", co.getFlags());
        assertEquals(user.getUsername(), co.getUsers());
    }

    private Operation createOp(OperationType opType, int timeOffset, Labware lw1, Labware lw2) {
        Operation op = entityCreator.simpleOp(opType, user, lw1, lw2);
        op.setPerformed(time(timeOffset));
        opsToUpdate.add(op);
        return op;
    }

    private static LocalDateTime time(int n) {
        return LocalDateTime.of(2025,1,1,12,0).plusDays(n);
    }

    private ReagentPlate createReagentPlate(String bc, String type) {
        ReagentPlate p = rpRepo.save(new ReagentPlate(bc, type));
        List<ReagentSlot> slots = Address.stream(p.getPlateLayout().getNumRows(), p.getPlateLayout().getNumColumns())
                .map(ad -> new ReagentSlot(p.getId(), ad))
                .toList();
        p.setSlots(asList(rsRepo.saveAll(slots)));
        return p;
    }
}
