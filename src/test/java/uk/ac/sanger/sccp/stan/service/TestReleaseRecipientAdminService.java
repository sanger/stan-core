package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;
import uk.ac.sanger.sccp.stan.repo.ReleaseRecipientRepo;

import javax.persistence.EntityExistsException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ReleaseRecipientAdminService}
 * @author dr6
 */
public class TestReleaseRecipientAdminService extends AdminServiceTestUtils<ReleaseRecipient, ReleaseRecipientRepo, ReleaseRecipientAdminService> {
    public TestReleaseRecipientAdminService() {
        super("Release recipient", ReleaseRecipient::new, ReleaseRecipientRepo::findByUsername, "Username not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(ReleaseRecipientRepo.class);
        service = new ReleaseRecipientAdminService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(ReleaseRecipientAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ReleaseRecipientAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }

    @ParameterizedTest
    @MethodSource("updateReleaseRecipientArgs")
    public void testUpdateFullName(String userName,  String fullName, String expectedUserName, Exception expectedException) {
        when(mockRepo.findByUsername(userName)).thenReturn(Optional.empty());
        if (expectedException != null) {
            assertException(expectedException, () -> service.updateFullName(userName, fullName));
            verify(mockRepo, never()).save(any());
            return;
        }
        ReleaseRecipient expectedResult = new ReleaseRecipient(20, userName, expectedUserName);
        when(mockRepo.findByUsername(userName)).thenReturn(Optional.of(new ReleaseRecipient(20, userName, null)));
        when(mockRepo.save(any())).thenReturn(expectedResult);
        assertSame(expectedResult, service.updateFullName(userName, fullName));
        verify(mockRepo).save( new ReleaseRecipient(20, userName, fullName));
    }

    private static Stream<Arguments> updateReleaseRecipientArgs() {
        Exception missingStringException = new IllegalArgumentException(MISSING_STRING_MESSAGE);
        return Stream.of(
                Arguments.of("Alpha", "Beta", "Beta", new EntityExistsException("Release recipient does not exist: Alpha")),
                Arguments.of("Alpha", "Beta\t\n", "Beta",null),
                Arguments.of("Alpha", "", "", null),
                Arguments.of("!Alpha", "Beta", "Beta", new IllegalArgumentException("string \"!Alpha\" contains invalid characters \"!\".")),
                Arguments.of(null, null, null, missingStringException),
                Arguments.of("Alpha", "\n", "", null)
        );
    }
}
