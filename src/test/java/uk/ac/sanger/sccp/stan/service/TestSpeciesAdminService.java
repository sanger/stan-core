package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.Species;
import uk.ac.sanger.sccp.stan.repo.SpeciesRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link SpeciesAdminService}
 * @author dr6
 */
public class TestSpeciesAdminService extends AdminServiceTestUtils<Species, SpeciesRepo, SpeciesAdminService> {
    public TestSpeciesAdminService() {
        super("Species", Species::new, SpeciesRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(SpeciesRepo.class);
        service = new SpeciesAdminService(mockRepo, mockUserRepo, simpleValidator(), mockEmailService);
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(SpeciesAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(SpeciesAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
