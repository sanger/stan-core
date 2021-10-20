package uk.ac.sanger.sccp.stan.service.work;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.CommentRepo;
import uk.ac.sanger.sccp.stan.repo.WorkEventRepo;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests {@link WorkEventServiceImp}
 * @author dr6
 */
public class TestWorkEventService {
    private WorkEventServiceImp eventService;

    private WorkEventRepo mockWorkEventRepo;
    private CommentRepo mockCommentRepo;

    @BeforeEach
    void setup() {
        mockWorkEventRepo = mock(WorkEventRepo.class);
        mockCommentRepo = mock(CommentRepo.class);

        eventService = spy(new WorkEventServiceImp(mockWorkEventRepo, mockCommentRepo));
    }

    @Test
    public void testRecordEvent() {
        when(mockWorkEventRepo.save(any())).then(Matchers.returnArgument());
        User user = new User(1, "user1", User.Role.normal);
        Comment comment = new Comment(30, "Hi", "Bananas");
        WorkEvent.Type type = WorkEvent.Type.pause;
        Work work = new Work(20, "SGP4000", null, null, null, null);

        WorkEvent event = eventService.recordEvent(user, work, type, comment);
        verify(mockWorkEventRepo).save(event);
        assertEquals(new WorkEvent(work, type, user, comment), event);
    }

    @ParameterizedTest
    @MethodSource("findEventTypeArgs")
    public void testFindEventType(Status oldStatus, Status newStatus, WorkEvent.Type expectedType) {
        if (expectedType==null) {
            assertThrows(IllegalArgumentException.class, () -> eventService.findEventType(oldStatus, newStatus));
        } else {
            assertEquals(expectedType, eventService.findEventType(oldStatus, newStatus));
        }
    }

    static Stream<Arguments> findEventTypeArgs() {
        return Arrays.stream(new Object[][] {
                { Status.unstarted, Status.active, WorkEvent.Type.start },
                { Status.paused, Status.active, WorkEvent.Type.resume },
                { Status.active, Status.completed, WorkEvent.Type.complete },
                { Status.active, Status.failed, WorkEvent.Type.fail },
                { Status.paused, Status.completed, WorkEvent.Type.complete },
                { Status.paused, Status.failed, WorkEvent.Type.fail },
                { Status.active, Status.paused, WorkEvent.Type.pause },
                { Status.active, null, null },
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("recordStatusChangeArgs")
    public void testRecordStatusChange(Status oldStatus, Status newStatus, Integer commentId, String expectedErrorMessage) {
        User user = new User(1, "user1", User.Role.normal);
        Work work = new Work(20, "SGP4000", null, null, null, oldStatus);

        Comment comment = (commentId==null ? null : new Comment(commentId, "Hello", "Bananas"));
        if (comment!=null) {
            when(mockCommentRepo.getById(commentId)).thenReturn(comment);
        }

        if (expectedErrorMessage==null) {
            WorkEvent event = new WorkEvent(work, WorkEvent.Type.resume, user, null);
            doReturn(event).when(eventService).recordEvent(any(), any(), any(), any());
            assertEquals(event, eventService.recordStatusChange(user, work, newStatus, commentId));
            WorkEvent.Type type = eventService.findEventType(oldStatus, newStatus);
            verify(eventService).recordEvent(user, work, type, comment);
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> eventService.recordStatusChange(user, work, newStatus, commentId)))
                    .hasMessage(expectedErrorMessage);
            verify(eventService, never()).recordEvent(any(), any(), any(), any());
        }
    }

    static Stream<Arguments> recordStatusChangeArgs() {
        return Arrays.stream(new Object[][]{
                {Status.paused, Status.paused, 10, "Work SGP4000 status is already paused."},
                {Status.completed, Status.active, null, "Cannot alter status of completed work: SGP4000"},
                {Status.failed, Status.completed, null, "Cannot alter status of failed work: SGP4000"},
                {Status.active, Status.paused, null, "A reason is required to pause work."},
                {Status.paused, Status.failed, null, "A reason is required to fail work."},
                {Status.paused, Status.active, 10, "A reason is not required to resume work."},
                {Status.active, Status.completed, 10, "A reason is not required to complete work."},
                {Status.active, Status.paused, 10, null},
                {Status.active, Status.completed, null, null},
                {Status.active, Status.failed, 10, null},
                {Status.paused, Status.active, null, null},
                {Status.paused, Status.failed, 10, null},
                {Status.unstarted, Status.active, null, null},
                {Status.active, Status.unstarted, null, "Cannot revert work status to unstarted."},
        }).map(Arguments::of);
    }
}
