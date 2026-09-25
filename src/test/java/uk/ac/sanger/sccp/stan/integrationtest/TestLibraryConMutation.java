package uk.ac.sanger.sccp.stan.integrationtest;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
public class TestLibraryConMutation {
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
    private final String RP_BARCODE = "111111111111111111111111";

    @Transactional
    @Test
    public void testLibraryCon() throws Exception {
        BioState bs = entityCreator.createBioState("Probes");
        Integer layoutId = tagLayoutRepo.layoutIdForReagentPlateType(ReagentPlate.REAGENT_PLATE_TYPES.get(1));
        assertNotNull(layoutId);
        Sample sample = entityCreator.createSample(null, null, bs);
        LabwareType lt2 = entityCreator.createLabwareType("lt2", 2, 2);
        Labware existingDest = entityCreator.createLabware("STAN-2", lt2, sample, sample);
        assertEquals(Labware.State.active, existingDest.getState());
        createReagentPlate(RP_BARCODE, ReagentPlate.REAGENT_PLATE_TYPES.get(1), layoutId);
        entityCreator.createOpType("Dual index plate", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.REAGENT_TRANSFER);
        entityCreator.createOpType("Amplification", null, OperationTypeFlag.IN_PLACE);
        Work work = entityCreator.createWork(null, null, null, null, null);
        String mutation = tester.readGraphQL("librarycon.graphql")
                .replace("[WORK]", work.getWorkNumber())
                .replace("[BC]", existingDest.getBarcode());
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        stubStorelightUnstore(mockStorelightClient);
        Object response = tester.post(mutation);
        assertNoErrors(response);
        Object opresdata = chainGet(response, "data", "libraryCon");
        List<Map<String,?>> lwData = chainGet(opresdata, "labware");
        List<Map<String,?>> opData = chainGet(opresdata, "operations");
        assertThat(opData.stream().map(od -> chainGet(od, "operationType", "name")))
                .containsExactly("Dual index plate", "Amplification");
        assertThat(lwData).hasSize(1);
        String destBarcode = chainGet(lwData, 0, "barcode");
        assertEquals(existingDest.getBarcode(), destBarcode);
        Labware lw = lwRepo.getByBarcode(destBarcode);
        Address[] addresses = {A1, A2};
        Arrays.stream(addresses).map(ad -> lw.getSlot(ad).getSamples())
                        .forEach(sams -> {
                            assertThat(sams).hasSize(1);
                            Sample sam = sams.getFirst();
                            assertEquals(bs, sam.getBioState());
                        });
        int[] opIds = opData.stream().mapToInt(od -> (Integer) od.get("id")).toArray();
        checkReagentTransfer(opIds[0], lw, layoutId);
        checkAmplification(opIds[1], lw);
        verifyNoInteractions(mockStorelightClient);
        entityManager.flush();
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
        ReagentPlate rp = reagentPlateRepo.getByBarcode(RP_BARCODE);
        assertEquals(ReagentPlate.REAGENT_PLATE_TYPES.get(1), rp.getPlateType());
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
        assertThat(measurements.stream().map(Measurement::getName)).allMatch("Cycles"::equals);
        List<OperationComment> opcoms = opComRepo.findAllByOperationIdIn(List.of(opId));
        assertThat(opcoms.stream().map(OperationComment::getSlotId)).containsExactly(slotIds);
        assertThat(opcoms.stream().mapToInt(oc -> oc.getComment().getId())).containsExactly(1,2);
    }
}
