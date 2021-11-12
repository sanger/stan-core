package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OpPassFail;
import uk.ac.sanger.sccp.stan.request.OpPassFail.SlotPassFail;
import uk.ac.sanger.sccp.stan.service.PassFailQueryService.AddressSampleId;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * Tests {@link PassFailQueryService}
 * @author dr6
 */
public class TestPassFailQueryService {
    private LabwareRepo mockLwRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationRepo mockOpRepo;
    private ResultOpRepo mockResultOpRepo;
    private OperationCommentRepo mockOpCommentRepo;

    private PassFailQueryService service;

    @BeforeEach
    void setup() {
        mockLwRepo = mock(LabwareRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);
        mockResultOpRepo = mock(ResultOpRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);

        service = spy(new PassFailQueryService(mockLwRepo, mockOpTypeRepo, mockOpRepo, mockResultOpRepo,
                mockOpCommentRepo));
    }

    @Test
    public void testGetPassFails() {
        Labware lw = EntityFactory.getTube();
        OperationType opType = EntityFactory.makeOperationType("QC", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.RESULT);

        when(mockLwRepo.getByBarcode(lw.getBarcode())).thenReturn(lw);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);

        List<Operation> ops = IntStream.range(0,3)
                .mapToObj(n -> new Operation(n, opType, time(3-n), null, null))
                .collect(toList());
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId())))
                .thenReturn(ops);

        List<List<SlotPassFail>> spfss = IntStream.range(0,ops.size())
                .mapToObj(i -> List.of(new SlotPassFail(new Address(1,1), PassFail.fail, "Comment "+i, List.of())))
                .collect(toList());
        for (int i = 0; i < ops.size(); ++i) {
            doReturn(spfss.get(i)).when(service).getSlotPassFails(lw, ops.get(i).getId());
        }

        List<OpPassFail> opfs = service.getPassFails(lw.getBarcode(), opType.getName());
        assertThat(opfs).hasSize(ops.size());
        for (int i = 0; i < ops.size(); ++i) {
            OpPassFail opf = opfs.get(2-i); // reverse the order, because the op dates had reversed order
            assertSame(ops.get(i), opf.getOperation());
            assertSame(spfss.get(i), opf.getSlotPassFails());
        }
    }

    static LocalDateTime time(int n) {
        return LocalDateTime.of(2021, 11, 11, 12, n);
    }

    @Test
    public void testGetSlotPassFails_empty() {
        final Integer opId = 555;
        when(mockResultOpRepo.findAllByOperationIdIn(any())).thenReturn(List.of());

        assertThat(service.getSlotPassFails(EntityFactory.getTube(), opId)).isEmpty();

        verify(service, never()).getCommentMap(any(), any());
        verify(mockResultOpRepo).findAllByOperationIdIn(List.of(opId));
    }

    @Test
    public void testGetSlotPassFails() {
        final Integer opId = 555;
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        LabwareType lt = EntityFactory.makeLabwareType(1, 3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);

        int[] sids = {50,51};
//        Sample[] samples = IntStream.range(0,sids.length)
//                .mapToObj(i -> new Sample(sids[i], 1+i, EntityFactory.getTissue(), EntityFactory.getBioState()))
//                .toArray(Sample[]::new);
        int[] slotIds = lw.getSlots().stream().mapToInt(Slot::getId).toArray();

        var commentMap = Map.of(
                new AddressSampleId(A1, sids[0]), Set.of("Alpha"),
                new AddressSampleId(A1, sids[1]), Set.of("Alpha"),
                new AddressSampleId(A2, sids[1]), new LinkedHashSet<>(List.of("Gamma", "Beta"))
        );

        doReturn(commentMap).when(service).getCommentMap(any(), any());

        List<ResultOp> ros = List.of(
                new ResultOp(1, PassFail.pass, opId, sids[0], slotIds[0], null),
                new ResultOp(2, PassFail.pass, opId, sids[1], slotIds[0], null),
                new ResultOp(3, PassFail.fail, opId, sids[0], slotIds[0], null),
                new ResultOp(4, PassFail.pass, opId, sids[1], slotIds[1], null),
                new ResultOp(5, PassFail.pass, opId, sids[0], slotIds[1]+1000, null)
        );

        when(mockResultOpRepo.findAllByOperationIdIn(List.of(opId))).thenReturn(ros);

        SlotPassFail[] expected = {
                new SlotPassFail(A1, PassFail.pass, "Alpha", List.of(sids[0], sids[1])),
                new SlotPassFail(A1, PassFail.fail, "Alpha", List.of(sids[0])),
                new SlotPassFail(A2, PassFail.pass, "Gamma\nBeta", List.of(sids[1]))
        };

        assertThat(service.getSlotPassFails(lw, opId)).containsExactlyInAnyOrder(expected);

        var slotMap = lw.getSlots().stream()
                .collect(toMap(Slot::getId, Function.identity()));
        verify(service).getCommentMap(slotMap, opId);
    }

    @Test
    public void testGetCommentMap() {
        final Integer opId = 55;
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(1,3));
        final Address A1 = new Address(1,1), A2 = new Address(1,2);
        Map<Integer, Slot> slotMap = lw.getSlots().stream().collect(toMap(Slot::getId, Function.identity()));
        Integer otherSlotId = slotMap.keySet().stream().max(Comparator.naturalOrder()).orElseThrow()+1;

        int[] slotids = lw.getSlots().stream().mapToInt(Slot::getId).toArray();
        int[] samids = { 100, 101, 102 };
        String[] texts = {"Alpha", "Beta", "Gamma"};
        Comment[] coms = IntStream.range(0, texts.length)
                .mapToObj(i -> new Comment(10+i, texts[i], "bananas"))
                .toArray(Comment[]::new);
        List<OperationComment> opComs = List.of(
                new OperationComment(1, coms[0], opId, samids[0], slotids[0], null),
                new OperationComment(2, coms[1], opId, samids[0], slotids[0], null),
                new OperationComment(3, coms[2], opId, samids[1], slotids[0], null),
                new OperationComment(4, coms[2], opId, samids[0], slotids[1], null),
                new OperationComment(5, coms[2], opId, samids[2], otherSlotId, null)
        );
        when(mockOpCommentRepo.findAllByOperationIdIn(List.of(opId))).thenReturn(opComs);

        var map = service.getCommentMap(slotMap, opId);

        assertThat(map).hasSize(3);
        assertThat(map.get(new AddressSampleId(A1, samids[0]))).containsExactly(texts[0], texts[1]);
        assertThat(map.get(new AddressSampleId(A1, samids[1]))).containsExactly(texts[2]);
        assertThat(map.get(new AddressSampleId(A2, samids[0]))).contains(texts[2]);
    }
}
