package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.model.CostCode;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.CostCodeRepo;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * Tests {@link CostCodeService}
 * @author dr6
 */
public class TestCostCodeService extends AdminServiceTestUtils<CostCode, CostCodeRepo, CostCodeService> {
    UserRepo mockUserRepo;
    EmailService mockEmailService;
    public TestCostCodeService() {
        super("CostCode", CostCode::new,
                CostCodeRepo::findByCode, "Code not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(CostCodeRepo.class);
        mockUserRepo = mock(UserRepo.class);
        mockEmailService = mock(EmailService.class);
        service = spy(new CostCodeService(mockRepo, mockUserRepo, simpleValidator(), mockEmailService));
    }

    @ParameterizedTest
    @MethodSource("addNewArgsUpCase")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(CostCodeService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @Test
    public void testAddNewByEndUser() {
        User creator = new User(100, "user1", User.Role.enduser);
        CostCode cc = new CostCode(200, "BANANA");
        when(mockRepo.save(any())).thenReturn(cc);
        doNothing().when(service).sendNewEntityEmail(any(), any());
        assertSame(cc, service.addNew(creator, "banana"));
        verify(service).sendNewEntityEmail(creator, cc);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSendNewEntityEmail(boolean anyAdmin) {
        List<User> adminUsers = anyAdmin ? List.of(new User(1, "admin1", User.Role.admin),
                new User(2, "admin2", User.Role.admin)) : List.of();
        when(mockUserRepo.findAllByRole(User.Role.admin)).thenReturn(adminUsers);
        CostCode item = new CostCode(100, "BANANAS");
        User creator = new User(1, "jeff", User.Role.enduser);

        service.sendNewEntityEmail(creator, item);

        if (!anyAdmin) {
            verifyNoInteractions(mockEmailService);
            return;
        }
        List<String> recipients = List.of("admin1", "admin2");
        verify(mockEmailService).tryEmail(recipients, "%service new CostCode",
                "User jeff has created a new CostCode on %service: BANANAS");
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(CostCodeService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
