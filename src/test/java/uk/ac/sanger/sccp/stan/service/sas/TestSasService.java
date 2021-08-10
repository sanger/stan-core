package uk.ac.sanger.sccp.stan.service.sas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.SasNumber.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;
import uk.ac.sanger.sccp.stan.repo.*;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SasServiceImp}
 * @author dr6
 */
public class TestSasService {
    private SasServiceImp sasService;

    private ProjectRepo mockProjectRepo;
    private CostCodeRepo mockCostCodeRepo;
    private SasNumberRepo mockSasRepo;
    private SasTypeRepo mockSasTypeRepo;
    private SasEventService mockSasEventService;

    @BeforeEach
    void setup() {
        mockProjectRepo = mock(ProjectRepo.class);
        mockCostCodeRepo = mock(CostCodeRepo.class);
        mockSasRepo = mock(SasNumberRepo.class);
        mockSasEventService = mock(SasEventService.class);
        mockSasTypeRepo = mock(SasTypeRepo.class);

        sasService = spy(new SasServiceImp(mockProjectRepo, mockCostCodeRepo, mockSasTypeRepo, mockSasRepo, mockSasEventService));
    }

    @Test
    public void testCreateSasNumber() {
        String projectName = "Stargate";
        String code = "S1234";
        String sasTypeName = "Drywalling";
        Project project = new Project(10, projectName);
        when(mockProjectRepo.getByName(projectName)).thenReturn(project);
        CostCode cc = new CostCode(20, code);
        when(mockCostCodeRepo.getByCode(code)).thenReturn(cc);
        SasType sasType = new SasType(30, sasTypeName);
        when(mockSasTypeRepo.getByName(sasTypeName)).thenReturn(sasType);
        String prefix = "SAS";
        String sasNum = "SAS4000";
        when(mockSasRepo.createNumber(prefix)).thenReturn(sasNum);
        User user = new User(1, "user1", User.Role.admin);
        when(mockSasRepo.save(any())).then(Matchers.returnArgument());

        SasNumber result = sasService.createSasNumber(user, prefix, sasTypeName, projectName, code);
        verify(sasService).checkPrefix(prefix);
        verify(mockSasRepo).createNumber(prefix);
        verify(mockSasRepo).save(result);
        verify(mockSasEventService).recordEvent(user, result, SasEvent.Type.create, null);
        assertEquals(new SasNumber(null, sasNum, sasType, project, cc, Status.active), result);
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    public void testUpdateStatus(boolean success) {
        User user = new User(1, "user1", User.Role.admin);
        String sasNum = "SAS4000";
        final Integer commentId = 99;
        Status newStatus = Status.paused;
        SasNumber sas = new SasNumber(10, sasNum, null, null, null, Status.active);
        when(mockSasRepo.getBySasNumber(sasNum)).thenReturn(sas);
        if (!success) {
            doThrow(IllegalArgumentException.class).when(mockSasEventService).recordStatusChange(any(), any(), any(), any());
            assertThrows(IllegalArgumentException.class, () -> sasService.updateStatus(user, sasNum, newStatus, commentId));
            verify(mockSasRepo, never()).save(any());
        } else {
            when(mockSasRepo.save(any())).then(Matchers.returnArgument());
            assertSame(sas, sasService.updateStatus(user, sasNum, newStatus, commentId));
            verify(mockSasRepo).save(sas);
            assertEquals(sas.getStatus(), newStatus);
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            "sas, ",
            "SAS, ",
            "r&d, ",
            "R&D, ",
            ", No prefix supplied for SAS number.",
            "Bananas, Invalid SAS number prefix: \"Bananas\"",
    })
    public void testCheckPrefix(String prefix, String expectedErrorMessage) {
        if (expectedErrorMessage==null) {
            sasService.checkPrefix(prefix);
        } else {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> sasService.checkPrefix(prefix));
            assertThat(ex).hasMessage(expectedErrorMessage);
        }
    }

    @ParameterizedTest
    @MethodSource("linkVariousArgs")
    public void testLinkVarious(Object arg, int numOps, String expectedErrorMsg) {
        String sasString;
        SasNumber sas;
        if (arg instanceof SasNumber) {
            sas = (SasNumber) arg;
            sasString = sas.getSasNumber();
            when(mockSasRepo.getBySasNumber(sasString)).thenReturn(sas);
        } else {
            sasString = (String) arg;
            sas = null;
            when(mockSasRepo.getBySasNumber(sasString)).then(invocation -> {
                throw new EntityNotFoundException("Unknown SAS number.");
            });
        }

        List<Operation> ops = (numOps==0 ? List.of() : List.of(new Operation()));
        if (sas==null) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> sasService.link(sasString, ops)))
                    .hasMessage(expectedErrorMsg);
        } else if (expectedErrorMsg==null) {
            assertSame(arg, sasService.link(sasString, ops));
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> sasService.link(sasString, ops)))
                    .hasMessage(expectedErrorMsg);
        }
        verify(mockSasRepo, never()).save(any());
    }

    static Stream<Arguments> linkVariousArgs() {
        SasNumber activeSas = new SasNumber(1, "SAS1001", null, null, null, Status.active);
        SasNumber pausedSas = new SasNumber(2, "R&D1002", null, null, null, Status.paused);
        SasNumber completedSas = new SasNumber(3, "SAS1003", null, null, null, Status.completed);
        SasNumber failedSas = new SasNumber(4, "SAS1004", null, null, null, Status.failed);

        return Arrays.stream(new Object[][] {
                { activeSas, 0, null },
                { pausedSas, 1, "R&D1002 cannot be used because it is paused." },
                { completedSas, 1, "SAS1003 cannot be used because it is completed." },
                { failedSas, 1, "SAS1004 cannot be used because it is failed." },
                { "SAS404", 1, "Unknown SAS number." },
        }).map(Arguments::of);
    }

    @Test
    public void testLinkSuccessful() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, sam1.getSection()+1, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, sam1, sam2);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.getFirstSlot().getSamples().add(sam1);
        lw2.getFirstSlot().getSamples().add(sam2);
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam1);

        OperationType opType = EntityFactory.makeOperationType("Section", null);
        Operation op1 = makeOp(opType, 10, lw0, lw1);
        Operation op2 = makeOp(opType, 11, lw0, lw2);

        SasNumber sas = new SasNumber(50, "SAS5000", null, null, null, Status.active);

        when(mockSasRepo.getBySasNumber(sas.getSasNumber())).thenReturn(sas);
        sas.setOperationIds(List.of(1,2,3));
        sas.setSampleSlotIds(List.of(new SampleSlotId(sam1.getId(), 2)));

        when(mockSasRepo.save(any())).then(Matchers.returnArgument());

        assertEquals(sas, sasService.link(sas.getSasNumber(), List.of(op1, op2)));
        verify(mockSasRepo).save(sas);
        assertThat(sas.getOperationIds()).containsExactlyInAnyOrder(1,2,3,10,11);
        assertThat(sas.getSampleSlotIds()).containsExactlyInAnyOrder(
                new SampleSlotId(sam1.getId(), 2),
                new SampleSlotId(sam1.getId(), lw1.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw1.getSlot(new Address(1,2)).getId()),
                new SampleSlotId(sam1.getId(), lw2.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw2.getFirstSlot().getId())
        );
    }

    @ParameterizedTest
    @MethodSource("getUsableSasArgs")
    public void testGetUsableSas(String sasString, Status status, Class<? extends Exception> expectedExceptionType) {
        if (expectedExceptionType==null) {
            SasNumber sas = new SasNumber(14, sasString, null, null, null, status);
            when(mockSasRepo.getBySasNumber(sasString)).thenReturn(sas);
            assertSame(sas, sasService.getUsableSas(sasString));
            return;
        }

        if (status==null) {
            when(mockSasRepo.getBySasNumber(sasString)).thenThrow(EntityNotFoundException.class);
        } else {
            SasNumber sas = new SasNumber(14, sasString, null, null, null, status);
            when(mockSasRepo.getBySasNumber(sasString)).thenReturn(sas);
        }
        assertThrows(expectedExceptionType, () -> sasService.getUsableSas(sasString));
    }

    static Stream<Arguments> getUsableSasArgs() {
        return Arrays.stream(new Object[][] {
                { "SAS5000", Status.active, null },
                { "SAS5000", Status.paused, IllegalArgumentException.class },
                { "SAS5000", Status.completed, IllegalArgumentException.class },
                { "SAS5000", Status.failed, IllegalArgumentException.class },
                { "SAS404", null, EntityNotFoundException.class },
                { null, null, NullPointerException.class },
        }).map(Arguments::of);
    }

    private Operation makeOp(OperationType opType, int opId, Labware srcLw, Labware dstLw) {
        List<Action> actions = new ArrayList<>();
        int acId = 100*opId;
        for (Slot src : srcLw.getSlots()) {
            for (Sample sam0 : src.getSamples()) {
                for (Slot dst : dstLw.getSlots()) {
                    for (Sample sam1 : dst.getSamples()) {
                        Action action = new Action(acId, opId, src, dst, sam1, sam0);
                        actions.add(action);
                        ++acId;
                    }
                }
            }
        }
        return new Operation(opId, opType, null, actions, EntityFactory.getUser());
    }
}
