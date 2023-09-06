package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;
import uk.ac.sanger.sccp.stan.repo.ReleaseRecipientRepo;

import javax.persistence.EntityExistsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    @Test
    public void testUpdateUserFullName_withEmptyFullName_shouldThrowException() {
        assertThat(assertThrows(IllegalArgumentException.class, () ->
                service.updateUserFullName("us1", "")))
                .hasMessage("User full name not supplied.");
    }

    @Test
    public void testUpdateUserFullName_withEmptyName_shouldThrowException() {
        assertThat(assertThrows(IllegalArgumentException.class, () ->
                service.updateUserFullName("", "User 1")))
                .hasMessage("Username not supplied.");
    }

    @Test
    public void testUpdateUserFullName_withUserNameDoesNotExist_shouldThrowException() {
        when(mockRepo.findByUsername(any())).thenReturn(Optional.empty());
        assertThat(assertThrows(EntityExistsException.class, () ->
                service.updateUserFullName("us", "Uriel South")))
                .hasMessage("Release recipient does not exist: us");

    }

    @Test
    public void testUpdateUserFullName() {
        ReleaseRecipient recipient = new ReleaseRecipient(1, "us", "user");
        when(mockRepo.findByUsername("us")).thenReturn(Optional.of(recipient));
        when(mockRepo.save(any())).thenReturn(recipient);
        ReleaseRecipient updatedRecipient = service.updateUserFullName("us", "Uriel South");
        assertThat(updatedRecipient.getUserFullName()).isEqualTo("Uriel South");

    }
}
