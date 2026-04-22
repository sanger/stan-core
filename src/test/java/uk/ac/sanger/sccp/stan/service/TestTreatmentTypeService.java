package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.TreatmentType;
import uk.ac.sanger.sccp.stan.repo.TreatmentTypeRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link TreatmentTypeService}
 * @author dr6
 */
public class TestTreatmentTypeService extends AdminServiceTestUtils<TreatmentType, TreatmentTypeRepo, TreatmentTypeService> {
    public TestTreatmentTypeService() {
        super("Treatment type", (id, name) -> new TreatmentType(id, name, true),
                TreatmentTypeRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(TreatmentTypeRepo.class);
        service = new TreatmentTypeService(mockRepo, simpleValidator(), mockTransactor);
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(TreatmentTypeService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(TreatmentTypeService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
