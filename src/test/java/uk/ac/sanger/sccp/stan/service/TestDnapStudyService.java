package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.DnapStudy;
import uk.ac.sanger.sccp.stan.repo.DnapStudyRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link DnapStudyService}
 * @author dr6
 */
public class TestDnapStudyService extends AdminServiceTestUtils<DnapStudy, DnapStudyRepo, DnapStudyService> {
    public TestDnapStudyService() {
        super("DNAP study", DnapStudy::new, DnapStudyRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(DnapStudyRepo.class);
        service = new DnapStudyService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(DnapStudyService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(DnapStudyService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
