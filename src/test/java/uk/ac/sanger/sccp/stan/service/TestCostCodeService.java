package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.CostCode;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.CostCodeRepo;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.mockTransactor;

/**
 * Tests {@link CostCodeService}
 * @author dr6
 */
public class TestCostCodeService extends AdminServiceTestUtils<CostCode, CostCodeRepo, CostCodeService> {

    public TestCostCodeService() {
        super("CostCode", CostCode::new,
                CostCodeRepo::findByCode, "Code not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(CostCodeRepo.class);
        service = spy(new CostCodeService(mockRepo, simpleValidator(), mockTransactor, mockNotifyService));
    }

    @ParameterizedTest
    @MethodSource("addNewArgsUpCase")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(CostCodeService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @Test
    public void testAddNewByEndUser() {
        mockTransactor(mockTransactor);
        User creator = new User(100, "user1", User.Role.enduser);
        CostCode cc = new CostCode(200, "BANANA");
        when(mockRepo.save(any())).thenReturn(cc);
        doNothing().when(service).sendNewEntityEmail(any(), any());
        assertSame(cc, service.addNew(creator, "banana"));
        verify(mockTransactor).transact(eq("Add CostCode"), any());
        verify(service).sendNewEntityEmail(creator, cc);
    }

    @Test
    public void testSendNewEntityEmail() {
        CostCode item = new CostCode(100, "BANANAS");
        User creator = new User(1, "jeff", User.Role.enduser);

        service.sendNewEntityEmail(creator, item);
        verify(mockNotifyService).issue("costcode", "%service new CostCode",
                "User jeff has created a new CostCode on %service: BANANAS");
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(CostCodeService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
