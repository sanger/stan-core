package uk.ac.sanger.sccp.stan.service.work;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.WorkType;
import uk.ac.sanger.sccp.stan.repo.WorkTypeRepo;
import uk.ac.sanger.sccp.stan.service.AdminServiceTestUtils;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link WorkTypeService}
 * @author dr6
 */
public class TestWorkTypeService extends AdminServiceTestUtils<WorkType, WorkTypeRepo, WorkTypeService> {
    public TestWorkTypeService() {
        super("WorkType", WorkType::new,
                WorkTypeRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(WorkTypeRepo.class);
        service = new WorkTypeService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(WorkTypeService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(WorkTypeService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
