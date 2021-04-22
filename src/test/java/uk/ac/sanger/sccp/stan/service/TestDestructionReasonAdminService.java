package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.DestructionReason;
import uk.ac.sanger.sccp.stan.repo.DestructionReasonRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link DestructionReasonAdminService}
 * @author dr6
 */
public class TestDestructionReasonAdminService extends AdminServiceTestUtils<DestructionReason, DestructionReasonRepo, DestructionReasonAdminService> {
    public TestDestructionReasonAdminService() {
        super("Destruction reason", DestructionReason::new,
                DestructionReasonRepo::findByText, "Text not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(DestructionReasonRepo.class);
        service = new DestructionReasonAdminService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(DestructionReasonAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(DestructionReasonAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
