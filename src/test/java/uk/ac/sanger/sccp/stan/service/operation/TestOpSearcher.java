package uk.ac.sanger.sccp.stan.service.operation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * Tests {@link OpSearcherImp}
 * @author dr6
 */
public class TestOpSearcher {
    private Ancestoriser mockAncestoriser;
    private OperationRepo mockOpRepo;

    private OpSearcherImp searcher;

    @BeforeEach
    void setup() {
        mockAncestoriser = mock(Ancestoriser.class);
        mockOpRepo = mock(OperationRepo.class);
        searcher = spy(new OpSearcherImp(mockAncestoriser, mockOpRepo));
    }

    @Test
    public void testFindLabwareOps() {
        OperationType opType = EntityFactory.makeOperationType("Paint", null);
        LabwareType lt = EntityFactory.makeLabwareType(2,3);
        Sample sam1 = new Sample(51, null, null, null);
        Sample sam2 = new Sample(52, null, null, null);
        Address B2 = new Address(2,2);
        List<Labware> labware = IntStream.rangeClosed(1,2).mapToObj(i -> {
            Labware lw = EntityFactory.makeEmptyLabware(lt);
            lw.setBarcode("STAN-A"+i);
            lw.setId(i);
            lw.getFirstSlot().addSample(sam1);
            lw.getFirstSlot().addSample(sam2);
            lw.getSlot(B2).addSample(i==1 ? sam1 : sam2);
            for (int j = 0; j < lw.getSlots().size(); ++j) {
                lw.getSlots().get(j).setId(10*i+j+1);
            }
            return lw;
        }).collect(toList());
        Ancestry ancestry = new Ancestry();
        when(mockAncestoriser.findAncestry(any())).thenReturn(ancestry);
        List<SlotSample> sss = List.of(
                new SlotSample(labware.get(0).getFirstSlot(), sam1),
                new SlotSample(labware.get(0).getFirstSlot(), sam2),
                new SlotSample(labware.get(0).getSlot(B2), sam1),
                new SlotSample(labware.get(1).getFirstSlot(), sam1),
                new SlotSample(labware.get(1).getFirstSlot(), sam2),
                new SlotSample(labware.get(1).getSlot(B2), sam2)
        );
        Map<Integer, Operation> opMap = Map.of(17, new Operation());
        doReturn(opMap).when(searcher).findLabwareOps(any(), any(), any());

        assertSame(opMap, searcher.findLabwareOps(opType, labware));
        verify(mockAncestoriser).findAncestry(sss);
        verify(searcher).findLabwareOps(opType, labware, ancestry);
    }


    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testFindLabwareOps_ancestry(boolean anyOps) {
        OperationType opType = EntityFactory.makeOperationType("Spoon", null);
        LabwareType lt = EntityFactory.getTubeType();
        Sample sam = EntityFactory.getSample();
        Labware[] labware = IntStream.rangeClosed(1,3).mapToObj(i -> {
            Labware lw = EntityFactory.makeLabware(lt, sam);
            lw.setBarcode("STAN-A"+i);
            lw.setId(i);
            lw.getFirstSlot().setId(50+i);
            return lw;
        }).toArray(Labware[]::new);
        Ancestry ancestry = new Ancestry();
        Slot[] slots = Arrays.stream(labware).map(Labware::getFirstSlot).toArray(Slot[]::new);
        ancestry.put(new SlotSample(slots[0], sam), Set.of(new SlotSample(slots[1], sam)));
        ancestry.put(new SlotSample(slots[1], sam), Set.of(new SlotSample(slots[2], sam)));

        Set<Integer> slotIds = Set.of(51, 52);

        if (!anyOps) {
            doReturn(List.of()).when(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(any(), any());
            assertThat(searcher.findLabwareOps(opType, Arrays.asList(labware), ancestry)).isEmpty();
            verify(searcher, never()).selectOp(any(), any(), any());
            verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
            return;
        }
        Operation[] ops = IntStream.range(0,2).mapToObj(i -> {
            int id = i+101;
            Operation op = new Operation();
            op.setId(id);
            Action a = new Action();
            a.setOperationId(id);
            a.setDestination(slots[i]);
            op.setActions(List.of(a));
            return op;
        }).toArray(Operation[]::new);
        doReturn(Arrays.asList(ops)).when(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(any(), any());

        doReturn(ops[0]).when(searcher).selectOp(same(labware[0]), any(), any());
        doReturn(ops[1]).when(searcher).selectOp(same(labware[1]), any(), any());
        doReturn(null).when(searcher).selectOp(same(labware[2]), any(), any());
        Map<Integer, Operation> expectedSlotIdMap = Map.of(slots[0].getId(), ops[0], slots[1].getId(), ops[1]);

        Map<Integer, Operation> expectedResult = Map.of(labware[0].getId(), ops[0], labware[1].getId(), ops[1]);

        assertEquals(expectedResult, searcher.findLabwareOps(opType, Arrays.asList(labware), ancestry));

        verify(mockOpRepo).findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        verify(searcher).selectOp(labware[0], ancestry, expectedSlotIdMap);
        verify(searcher).selectOp(labware[1], ancestry, expectedSlotIdMap);
        verify(searcher).selectOp(labware[2], ancestry, expectedSlotIdMap);
    }

    @ParameterizedTest
    @CsvSource({"1,1,1", "1,2,2", "2,1,2", "1,,1", "2,,2", ",,"})
    public void testSelectGreater(Integer a, Integer b, Integer expected) {
        assertEquals(expected, searcher.selectGreater(a, b));
    }

    @ParameterizedTest
    @MethodSource("selectOpArgs")
    public void testSelectOp(Labware lw, Ancestry ancestry, Map<Integer, Operation> lwOp, Operation expected) {
        assertSame(expected, searcher.selectOp(lw, ancestry, lwOp));
    }

    static Stream<Arguments> selectOpArgs() {
        LabwareType lt = EntityFactory.makeLabwareType(1,3);
        final Address A2 = new Address(1,2);
        Sample sam = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(lt, sam, sam); // Slots 11,12,13
        Labware lw2 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam); // Slot 21
        Labware lw3 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam); // Slot 31
        Labware lw4 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam); // Slot 41
        Labware lw5 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam); // Slot 51
        final List<Labware> labware = List.of(lw1, lw2, lw3, lw4, lw5);
        for (int i = 0; i < labware.size(); ++i) {
            int id = i+1;
            Labware lw = labware.get(i);
            lw.setId(id);
            for (int j = 0; j < lw.getSlots().size(); ++j) {
                lw.getSlots().get(j).setId(10*id + j + 1);
            }
        }


        final IntFunction<SlotSample> ssFn = i -> new SlotSample(labware.get(i).getFirstSlot(), sam);

        Ancestry anc = new Ancestry();
        anc.put(ssFn.apply(0), Set.of(ssFn.apply(1)));
        anc.put(ssFn.apply(1), Set.of(ssFn.apply(2), ssFn.apply(3)));
        anc.put(new SlotSample(lw1.getSlot(A2), sam), Set.of(ssFn.apply(4)));

        Operation op1 = new Operation();
        op1.setId(101);
        op1.setPerformed(LocalDateTime.now());
        Operation op2 = new Operation();
        op2.setId(102);
        op2.setPerformed(LocalDateTime.now());

        return Arrays.stream(new Object[][] {
                {lw1, anc, Map.of(51, op1), op1},
                {lw1, anc, Map.of(41, op1), op1},
                {lw1, anc, Map.of(41,op1, 21,op2), op2},
                {lw2, anc, Map.of(51, op1), null},
        }).map(Arguments::of);
    }
}
