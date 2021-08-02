package uk.ac.sanger.sccp.stan.service.sas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;
import uk.ac.sanger.sccp.stan.repo.*;

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
    private SasEventService mockSasEventService;

    @BeforeEach
    void setup() {
        mockProjectRepo = mock(ProjectRepo.class);
        mockCostCodeRepo = mock(CostCodeRepo.class);
        mockSasRepo = mock(SasNumberRepo.class);
        mockSasEventService = mock(SasEventService.class);

        sasService = spy(new SasServiceImp(mockProjectRepo, mockCostCodeRepo, mockSasRepo, mockSasEventService));
    }

    @Test
    public void testCreateSasNumber() {
        String projectName = "Stargate";
        String code = "S1234";
        Project project = new Project(10, projectName);
        when(mockProjectRepo.getByName(projectName)).thenReturn(project);
        CostCode cc = new CostCode(20, code);
        when(mockCostCodeRepo.getByCode(code)).thenReturn(cc);
        String prefix = "SAS";
        String sasNum = "SAS4000";
        when(mockSasRepo.createNumber(prefix)).thenReturn(sasNum);
        User user = new User(1, "user1", User.Role.admin);
        when(mockSasRepo.save(any())).then(Matchers.returnArgument());

        SasNumber result = sasService.createSasNumber(user, prefix, projectName, code);
        verify(sasService).checkPrefix(prefix);
        verify(mockSasRepo).createNumber(prefix);
        verify(mockSasRepo).save(result);
        verify(mockSasEventService).recordEvent(user, result, SasEvent.Type.create, null);
        assertEquals(new SasNumber(null, sasNum, project, cc, Status.active), result);
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    public void testUpdateStatus(boolean success) {
        User user = new User(1, "user1", User.Role.admin);
        String sasNum = "SAS4000";
        final Integer commentId = 99;
        Status newStatus = Status.paused;
        SasNumber sas = new SasNumber(10, sasNum, null, null, Status.active);
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
}
