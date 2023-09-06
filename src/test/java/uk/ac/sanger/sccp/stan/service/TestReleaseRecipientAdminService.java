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
    @MethodSource("addReleaseRecipientArgs")
    public void testAddNew(String userName,  String userFullName, String expectedUserName, Exception expectedException) {
        when(mockRepo.findByUsername(userName)).thenReturn(Optional.of(new ReleaseRecipient()));
        if (expectedException != null) {
            assertException(expectedException, () -> service.addNew(userName, userFullName));
            verify(mockRepo, never()).save(any());
            return;
        }
        ReleaseRecipient expectedResult = new ReleaseRecipient(20, expectedUserName, userFullName);
        when(mockRepo.save(any())).thenReturn(expectedResult);
        when(mockRepo.findByUsername(userName)).thenReturn(Optional.empty());
        assertSame(expectedResult, service.addNew(userName, userFullName));
        verify(mockRepo).save( new ReleaseRecipient(null, userName, userFullName));
    }


    protected static Stream<Arguments> addReleaseRecipientArgs() {
        Exception missingStringException = new IllegalArgumentException(MISSING_STRING_MESSAGE);
        return Stream.of(
                Arguments.of("Alpha", "Beta", "Alpha", null),
                Arguments.of("Alpha", "Beta ? 7 contains @ $ different ! characters\t\n", "Alpha", null),
                Arguments.of("   Alpha\t\n", "", "Alpha", null),
                Arguments.of("!Alpha", null, null, new IllegalArgumentException("string \"!Alpha\" contains invalid characters \"!\".")),
                Arguments.of(null, null, null, missingStringException),
                Arguments.of("   \n", null, null, missingStringException),
                Arguments.of("Alpha", "Alpha", "Alpha", new EntityExistsException("<ENTITY> already exists: Alpha"))
        );
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ReleaseRecipientAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }

    @ParameterizedTest
    @MethodSource("updateReleaseRecipientArgs")
    public void testUpdateUserFullName(String userName,  String userFullName, String expectedUserName, Exception expectedException) {
        when(mockRepo.findByUsername(userName)).thenReturn(Optional.empty());
        if (expectedException != null) {
            assertException(expectedException, () -> service.updateUserFullName(userName, userFullName));
            verify(mockRepo, never()).save(any());
            return;
        }
        ReleaseRecipient expectedResult = new ReleaseRecipient(20, userName, expectedUserName);
        when(mockRepo.findByUsername(userName)).thenReturn(Optional.of(new ReleaseRecipient(20, userName, null)));
        when(mockRepo.save(any())).thenReturn(expectedResult);
        assertSame(expectedResult, service.updateUserFullName(userName, userFullName));
        verify(mockRepo).save( new ReleaseRecipient(20, userName, userFullName));
    }

    protected static Stream<Arguments> updateReleaseRecipientArgs() {
        Exception missingStringException = new IllegalArgumentException(MISSING_STRING_MESSAGE);
        return Stream.of(
                Arguments.of("Alpha", "Beta", "Beta", new EntityExistsException("Release recipient does not exist: Alpha")),
                Arguments.of("Alpha", "Beta\t\n", "Beta",null),
                Arguments.of("Alpha\t\n", "", "", null),
                Arguments.of("!Alpha", "Beta", "Beta", new IllegalArgumentException("string \"!Alpha\" contains invalid characters \"!\".")),
                Arguments.of(null, null, null, missingStringException),
                Arguments.of("Alpha", "\n", "", null)
        );
    }
}
