package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.CostCode;
import uk.ac.sanger.sccp.stan.repo.CostCodeRepo;

import static org.mockito.Mockito.mock;

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
        service = new CostCodeService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgsUpCase")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(CostCodeService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(CostCodeService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
