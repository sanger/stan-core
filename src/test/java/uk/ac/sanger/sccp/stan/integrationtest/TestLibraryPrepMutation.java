package uk.ac.sanger.sccp.stan.integrationtest;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.LibraryPrepRequest;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.asList;

/**
 * Tests that {@link LibraryPrepRequest} is performed correctly.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Sql("/testdata/tag_layout_setup.sql")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestLibraryPrepMutation {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityCreator entityCreator;
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private LabwareRepo lwRepo;
    @Autowired
    private OperationRepo opRepo;
    @Autowired
    private LabwareNoteRepo noteRepo;
    @Autowired
    private ReagentPlateRepo reagentPlateRepo;
    @Autowired
    private ReagentSlotRepo reagentSlotRepo;
    @Autowired
    private ReagentActionRepo reagentActionRepo;
    @Autowired
    private MeasurementRepo measurementRepo;
    @Autowired
    private OperationCommentRepo opComRepo;
    @Autowired
    private TagLayoutRepo tagLayoutRepo;
    @MockBean
    StorelightClient mockStorelightClient;

    private final Address A1 = new Address(1,1), A2 = new Address(1,2);

    @Transactional
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLibraryPrep(boolean variant) throws Exception {
        Sample sample = entityCreator.createSample(null, null);
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 2);
        Labware sourceLw = entityCreator.createLabware("STAN-1", lt, sample, sample);
        BioState bs = entityCreator.createBioState("Probes");
        Integer layoutId = tagLayoutRepo.layoutIdForReagentPlateType(ReagentPlate.TYPE_FFPE);
        assertNotNull(layoutId);
        Labware existingDest;
        if (variant) {
            Sample probeSample = entityCreator.createSample(sample.getTissue(), null, bs);
            LabwareType lt2 = entityCreator.createLabwareType("lt2", 2, 2);
            existingDest = entityCreator.createLabware("STAN-2", lt2, null, null, probeSample, probeSample);
            assertEquals(Labware.State.active, existingDest.getState());
            createReagentPlate("012345678901234567890123", ReagentPlate.TYPE_FFPE, layoutId);
        } else {
            existingDest = null;
        }
        entityCreator.createOpType("Transfer", null, OperationTypeFlag.ACTIVE_DEST);
        entityCreator.createOpType("Dual index plate", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.REAGENT_TRANSFER);
        entityCreator.createOpType("Amplification", null, OperationTypeFlag.IN_PLACE);
        Work work = entityCreator.createWork(null, null, null, null, null);
        String mutation = tester.readGraphQL(variant ? "libraryprepexistant.graphql" : "libraryprep.graphql")
                .replace("[WORK]", work.getWorkNumber());
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        stubStorelightUnstore(mockStorelightClient);
        Object response = tester.post(mutation);
        Object opresdata = chainGet(response, "data", "libraryPrep");
        List<Map<String,?>> lwData = chainGet(opresdata, "labware");
        List<Map<String,?>> opData = chainGet(opresdata, "operations");
        assertThat(opData.stream().map(od -> chainGet(od, "operationType", "name")))
                .containsExactly("Transfer", "Dual index plate", "Amplification");
        assertThat(lwData).hasSize(1);
        String destBarcode = chainGet(lwData, 0, "barcode");
        assertNotEquals(sourceLw.getBarcode(), destBarcode);
        if (existingDest!=null) {
            assertEquals(existingDest.getBarcode(), destBarcode);
        }
        Labware lw = lwRepo.getByBarcode(destBarcode);
        Address[] addresses = {A1, A2};
        Arrays.stream(addresses).map(ad -> lw.getSlot(ad).getSamples())
                        .forEach(sams -> {
                            assertThat(sams).hasSize(1);
                            Sample sam = sams.getFirst();
                            assertEquals(bs, sam.getBioState());
                        });
        int[] opIds = opData.stream().mapToInt(od -> (Integer) od.get("id")).toArray();
        checkTransfer(opIds[0], sourceLw, lw);
        checkReagentTransfer(opIds[1], lw, layoutId);
        checkAmplification(opIds[2], lw);
        if (variant) {
            verifyNoInteractions(mockStorelightClient);
        } else {
            verifyStorelightQuery(mockStorelightClient, List.of("unstoreBarcodes", sourceLw.getBarcode()), user.getUsername());
        }
        entityManager.refresh(work);
        assertThat(work.getOperationIds()).containsExactlyInAnyOrderElementsOf(Arrays.stream(opIds).boxed()::iterator);
    }

    @NotNull
    private ReagentPlate createReagentPlate(String barcode, String plateType, Integer layoutId) {
        ReagentPlate reagentPlate = reagentPlateRepo.save(new ReagentPlate(barcode, plateType, layoutId));
        final ReagentPlateLayout plateLayout = reagentPlate.getPlateLayout();
        Integer rpId = reagentPlate.getId();
        var slots = reagentSlotRepo.saveAll(Address.stream(plateLayout.getNumRows(), plateLayout.getNumColumns())
                .map(ad -> new ReagentSlot(null, rpId, ad, false))
                .toList());
        reagentPlate.setSlots(asList(slots));
        return reagentPlate;
    }

    private void checkTransfer(int opId, Labware sourceLw, Labware lw) {
        Operation transfer = opRepo.findById(opId).orElseThrow();
        assertEquals("Transfer", transfer.getOperationType().getName());
        List<Action> transferActions = transfer.getActions();
        assertThat(transferActions.stream()
                .map(Action::getSource)).containsExactly(sourceLw.getSlot(A1), sourceLw.getSlot(A2));
        assertThat(transferActions.stream()
                .map(Action::getDestination)).containsExactly(lw.getSlot(A1), lw.getSlot(A2));
        List<LabwareNote> notes = noteRepo.findAllByOperationIdIn(List.of(transfer.getId()));
        assertThat(notes).hasSize(3);
        assertThat(notes).allMatch(note -> note.getLabwareId().equals(lw.getId()));
        Map<String, String> noteMap = notes.stream()
                .collect(toMap(LabwareNote::getName, LabwareNote::getValue));
        assertEquals("Faculty", noteMap.get("costing"));
        assertEquals("123456", noteMap.get("lot"));
        assertEquals("234567", noteMap.get("probe lot"));
    }

    private void checkReagentTransfer(int opId, Labware lw, Integer layoutId) {
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals("Dual index plate", op.getOperationType().getName());
        List<Action> actions = op.getActions();
        actions.forEach(ac -> assertEquals(ac.getSource(), ac.getDestination()));
        Slot[] transferSlots = Stream.of(A1, A2).map(lw::getSlot).toArray(Slot[]::new);
        Slot[] filledSlots = lw.getSlots().stream().filter(slot -> !slot.getSamples().isEmpty()).toArray(Slot[]::new);
        assertThat(actions.stream().map(Action::getDestination)).containsExactlyInAnyOrder(filledSlots);
        List<ReagentAction> ras = reagentActionRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(ras.stream().map(ReagentAction::getDestination)).containsExactlyInAnyOrder(transferSlots);
        ReagentPlate rp = reagentPlateRepo.getByBarcode("012345678901234567890123");
        assertEquals(ReagentPlate.TYPE_FFPE, rp.getPlateType());
        assertEquals(layoutId, rp.getTagLayoutId());
        assertThat(ras.stream().map(ReagentAction::getReagentSlot)).containsExactly(rp.getSlot(A1), rp.getSlot(A2));
    }

    private void checkAmplification(int opId, Labware lw) {
        Operation op = opRepo.findById(opId).orElseThrow();
        assertEquals("Amplification", op.getOperationType().getName());
        List<Action> actions = op.getActions();
        actions.forEach(ac -> assertEquals(ac.getSource(), ac.getDestination()));
        Slot[] filledSlots = lw.getSlots().stream().filter(slot -> !slot.getSamples().isEmpty()).toArray(Slot[]::new);
        assertThat(actions.stream().map(Action::getDestination)).containsExactlyInAnyOrder(filledSlots);
        List<Measurement> measurements = measurementRepo.findAllByOperationIdIn(List.of(opId));
        final Integer[] slotIds = {lw.getSlot(A1).getId(), lw.getSlot(A2).getId()};
        assertThat(measurements.stream().map(Measurement::getSlotId)).containsExactly(slotIds);
        assertThat(measurements.stream()
                        .mapToInt(meas -> (int) Double.parseDouble(meas.getValue()))).containsExactly(10, 20);
        assertThat(measurements.stream().map(Measurement::getName)).allMatch("Cq value"::equals);
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opcoms.stream().map(OperationComment::getSlotId)).containsExactly(slotIds);
        assertThat(opcoms.stream().mapToInt(oc -> oc.getComment().getId())).containsExactly(1,2);
    }
}
