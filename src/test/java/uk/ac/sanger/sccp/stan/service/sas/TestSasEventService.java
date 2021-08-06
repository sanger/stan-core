package uk.ac.sanger.sccp.stan.service.sas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;
import uk.ac.sanger.sccp.stan.repo.SasEventRepo;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests {@link SasEventServiceImp}
 * @author dr6
 */
public class TestSasEventService {
    private SasEventServiceImp eventService;

    private SasEventRepo mockSasEventRepo;
    private CommentRepo mockCommentRepo;

    @BeforeEach
    void setup() {
        mockSasEventRepo = mock(SasEventRepo.class);
        mockCommentRepo = mock(CommentRepo.class);

        eventService = spy(new SasEventServiceImp(mockSasEventRepo, mockCommentRepo));
    }

    @Test
    public void testRecordEvent() {
        when(mockSasEventRepo.save(any())).then(Matchers.returnArgument());
        User user = new User(1, "user1", User.Role.normal);
        Comment comment = new Comment(30, "Hi", "Bananas");
        SasEvent.Type type = SasEvent.Type.pause;
        SasNumber sas = new SasNumber(20, "SAS4000", null, null, null, null);

        SasEvent event = eventService.recordEvent(user, sas, type, comment);
        verify(mockSasEventRepo).save(event);
        assertEquals(new SasEvent(sas, type, user, comment), event);
    }

    @ParameterizedTest
    @MethodSource("findEventTypeArgs")
    public void testFindEventType(Status newStatus, SasEvent.Type expectedType) {
        if (expectedType==null) {
            assertThrows(IllegalArgumentException.class, () -> eventService.findEventType(newStatus));
        } else {
            assertEquals(expectedType, eventService.findEventType(newStatus));
        }
    }

    static Stream<Arguments> findEventTypeArgs() {
        return Arrays.stream(new Object[][] {
                { Status.active, SasEvent.Type.resume },
                { Status.completed, SasEvent.Type.complete },
                { Status.failed, SasEvent.Type.fail },
                { Status.paused, SasEvent.Type.pause },
                { null, null },
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("recordStatusChangeArgs")
    public void testRecordStatusChange(Status oldStatus, Status newStatus, Integer commentId, String expectedErrorMessage) {
        User user = new User(1, "user1", User.Role.normal);
        SasNumber sas = new SasNumber(20, "SAS4000", null, null, null, oldStatus);

        Comment comment = (commentId==null ? null : new Comment(commentId, "Hello", "Bananas"));
        if (comment!=null) {
            when(mockCommentRepo.getById(commentId)).thenReturn(comment);
        }

        if (expectedErrorMessage==null) {
            SasEvent event = new SasEvent(sas, SasEvent.Type.resume, user, null);
            doReturn(event).when(eventService).recordEvent(any(), any(), any(), any());
            assertEquals(event, eventService.recordStatusChange(user, sas, newStatus, commentId));
            SasEvent.Type type = eventService.findEventType(newStatus);
            verify(eventService).recordEvent(user, sas, type, comment);
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> eventService.recordStatusChange(user, sas, newStatus, commentId)))
                    .hasMessage(expectedErrorMessage);
            verify(eventService, never()).recordEvent(any(), any(), any(), any());
        }
    }

    static Stream<Arguments> recordStatusChangeArgs() {
        return Arrays.stream(new Object[][]{
                {Status.paused, Status.paused, 10, "SAS number SAS4000 status is already paused."},
                {Status.completed, Status.active, null, "Cannot alter status of completed SAS number: SAS4000"},
                {Status.failed, Status.completed, null, "Cannot alter status of failed SAS number: SAS4000"},
                {Status.active, Status.paused, null, "A reason is required to pause an SAS number."},
                {Status.paused, Status.failed, null, "A reason is required to fail an SAS number."},
                {Status.paused, Status.active, 10, "A reason is not required to resume an SAS number."},
                {Status.active, Status.completed, 10, "A reason is not required to complete an SAS number."},
                {Status.active, Status.paused, 10, null},
                {Status.active, Status.completed, null, null},
                {Status.active, Status.failed, 10, null},
                {Status.paused, Status.active, null, null},
                {Status.paused, Status.failed, 10, null},
        }).map(Arguments::of);
    }
}
