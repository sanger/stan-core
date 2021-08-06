package uk.ac.sanger.sccp.stan.service.sas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.SasType;
import uk.ac.sanger.sccp.stan.repo.SasTypeRepo;
import uk.ac.sanger.sccp.stan.service.AdminServiceTestUtils;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link SasTypeService}
 * @author dr6
 */
public class TestSasTypeService extends AdminServiceTestUtils<SasType, SasTypeRepo, SasTypeService> {
    public TestSasTypeService() {
        super("SasType", SasType::new,
                SasTypeRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(SasTypeRepo.class);
        service = new SasTypeService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(SasTypeService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(SasTypeService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
